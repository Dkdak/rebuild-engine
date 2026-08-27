package com.mteam.rebuildengine.service.analysis;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.model.entity.LanduseDistrictEntity;
import com.mteam.rebuildengine.model.entity.LanduseEntity;
import com.mteam.rebuildengine.model.entity.PermitEntity;
import com.mteam.rebuildengine.model.entity.ZoningLimitEntity;
import com.mteam.rebuildengine.model.read.AgingRequirementReadModel;
import com.mteam.rebuildengine.model.response.RemodelingBasisResponse;
import com.mteam.rebuildengine.model.response.RemodelingResultResponse;
import com.mteam.rebuildengine.model.response.RemodelingVerdict;
import com.mteam.rebuildengine.repository.BuildingRepository;
import com.mteam.rebuildengine.repository.LanduseDistrictRepository;
import com.mteam.rebuildengine.repository.LanduseRepository;
import com.mteam.rebuildengine.repository.PermitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// docs/law/LAW-001 §5 "F-06 적용 로직 (V1)" — 게이트(리모델링 허용연한, §1) → 노후·불량건축물 연한
// 등급 경계(§2/§3, 별표1) → 진행중 개발행위 오버라이드(§4) 3단 구조. 법령 수치는 전부 DB 참조
// 테이블(aging_requirement/permit_validity_period/zoning_limit)에서 조회하고 코드에 하드코딩하지
// 않는다(LAW-001 §5 "if(서울) return 30 금지"). "가능/제한적가능" 자체의 경계(requiredYears)는
// 법령으로 확정됐지만, 그 경계를 넘긴 정도(achievementRate)를 화면에 얼마나 강조할지 등 세부는
// 여전히 기획자 검토 대상(FEATURE_06 §5.1).
@Service
@RequiredArgsConstructor
public class RemodelingServiceImpl implements RemodelingService {

    private static final Set<String> IN_PROGRESS_BUILD_TYPES = Set.of("철거", "재축", "신축");
    private static final String HOUSING_TYPE_MULTI_FAMILY = "공동주택";
    private static final String HOUSING_TYPE_OTHER = "비주거용";
    private static final String STRUCTURE_GROUP_RC = "RC_SRC_SC";
    private static final String STRUCTURE_GROUP_OTHER = "OTHER";
    private static final String FLOOR_TIER_5_UP = "5층이상";
    private static final String FLOOR_TIER_4_DOWN = "4층이하";
    private static final int CURVE_YEAR_MIN = 1981;
    private static final int CURVE_YEAR_MAX = 1991;
    private static final int APARTMENT_MIN_FLOORS = 5;

    private final BuildingRepository buildingRepository;
    private final LanduseRepository landuseRepository;
    private final LanduseDistrictRepository landuseDistrictRepository;
    private final PermitRepository permitRepository;
    private final ReferenceDataCache referenceDataCache;

    @Override
    public Optional<RemodelingResultResponse> getRemodelingResult(String buildingId) {
        return buildingRepository.findByBdrgSnAndIsAncillaryFalseAndIsOutOfScopeFalseAndIsDeletedFalse(buildingId).map(building -> evaluate(building,
                permitRepository.findByBuildingIdOrderByPermitDateDesc(building.getBdrgSn()),
                landuseRepository.findByBuildingId(building.getBdrgSn()),
                landuseDistrictRepository.findByBuildingId(building.getBdrgSn())));
    }

    // F-09 V1 배치(InvestmentAnalysisBatchService) — 페이지 단위로 미리 벌크 조회한 값을 그대로 쓴다.
    // 계산 로직은 getRemodelingResult(String)와 완전히 동일, 조회 방식만 다르다.
    @Override
    public RemodelingResultResponse getRemodelingResult(BuildingEntity building, BuildingDataBundle bundle) {
        return evaluate(building, bundle.permits(building.getBdrgSn()), bundle.landuse(building.getBdrgSn()),
                bundle.landuseDistricts(building.getBdrgSn()));
    }

    private RemodelingResultResponse evaluate(BuildingEntity building, List<PermitEntity> permits,
                                               List<LanduseEntity> landuses, List<LanduseDistrictEntity> landuseDistrictEntities) {
        PermitEntity recentPermit = permits.isEmpty() ? null : permits.get(0);
        int permitValidityYears = referenceDataCache.permitValidityYears();
        boolean permitInProgress = permits.stream().anyMatch(p -> isInProgress(p, permitValidityYears));

        String zoneName = null;
        BigDecimal floorAreaRatioLimit = null;
        BigDecimal floorAreaRatioSurplus = null;
        BigDecimal additionalBuildableAreaSqm = null;
        Integer estimatedAdditionalHouseholds = null;
        if (!landuses.isEmpty()) {
            zoneName = landuses.get(0).getZoneName();
            Optional<ZoningLimitEntity> zoningLimit = referenceDataCache.zoningLimit(zoneName);
            if (zoningLimit.isPresent() && building.getFart() != null) {
                floorAreaRatioLimit = zoningLimit.get().getFloorAreaRatioLimit();
                floorAreaRatioSurplus = floorAreaRatioLimit.subtract(building.getFart());
                if (building.getSiar() != null) {
                    // LAW-002 §4 V1 — 증축가능면적(이론상) = 용적률 여유 × 대지면적. 여유가 음수면 0.
                    additionalBuildableAreaSqm = floorAreaRatioSurplus.max(BigDecimal.ZERO)
                            .divide(BigDecimal.valueOf(100))
                            .multiply(building.getSiar())
                            .setScale(2, RoundingMode.HALF_UP);
                    estimatedAdditionalHouseholds = estimateAdditionalHouseholds(building, additionalBuildableAreaSqm);
                }
            }
        }

        List<String> districtNames = landuseDistrictEntities.stream()
                .map(LanduseDistrictEntity::getDistrictName).distinct().toList();

        String recentPermitType = recentPermit == null ? null : recentPermit.getBuildType();
        LocalDate recentPermitDate = recentPermit == null ? null : recentPermit.getPermitDate();

        Integer buildingAgeYears = building.getUseAprvYmd() == null ? null
                : Period.between(building.getUseAprvYmd(), LocalDate.now()).getYears();

        // §3.4 예외 — 사용승인일 없으면 노후도 게이트 자체를 판정할 수 없다. 진행중 개발행위만으로
        // "가능"을 주장할 근거는 없으므로 보수적으로 불가 처리(근거 없이 가능 주장 금지).
        if (buildingAgeYears == null) {
            RemodelingBasisResponse basis = new RemodelingBasisResponse(null, false, null, null,
                    zoneName, districtNames, floorAreaRatioLimit, floorAreaRatioSurplus, building.getFart(),
                    building.getSiar(), building.getGfa(), building.getFartCmpttnGfa(),
                    additionalBuildableAreaSqm, estimatedAdditionalHouseholds, recentPermitType, recentPermitDate, permitInProgress);
            return new RemodelingResultResponse(null, RemodelingVerdict.NOT_POSSIBLE, basis);
        }

        AgingRequirementReadModel requirement = lookupAgingRequirement(building, buildingAgeYears);
        boolean gatePassed = buildingAgeYears >= requirement.gateYears();
        int achievementRate = Math.round(buildingAgeYears * 100f / requirement.requiredYears());

        RemodelingBasisResponse basis = new RemodelingBasisResponse(
                buildingAgeYears, gatePassed, requirement.gateYears(), requirement.requiredYears(),
                zoneName, districtNames, floorAreaRatioLimit, floorAreaRatioSurplus, building.getFart(),
                building.getSiar(), building.getGfa(), building.getFartCmpttnGfa(),
                additionalBuildableAreaSqm, estimatedAdditionalHouseholds, recentPermitType, recentPermitDate, permitInProgress
        );

        RemodelingVerdict verdict;
        if (permitInProgress || !gatePassed) {
            verdict = RemodelingVerdict.NOT_POSSIBLE;
        } else if (buildingAgeYears < requirement.requiredYears()) {
            verdict = RemodelingVerdict.LIMITED;
        } else {
            verdict = RemodelingVerdict.POSSIBLE;
        }

        return new RemodelingResultResponse(achievementRate, verdict, basis);
    }

    // docs/law/LAW-001 §2 — 공동주택 여부는 건축물대장 주용도명이 "공동주택"인지로 판정(아파트/
    // 연립다세대 구분 불필요, 둘 다 같은 §2 규칙 적용).
    private AgingRequirementReadModel lookupAgingRequirement(BuildingEntity building, int buildingAgeYears) {
        String housingType = HOUSING_TYPE_MULTI_FAMILY.equals(building.getMnUsgCdNm())
                ? HOUSING_TYPE_MULTI_FAMILY : HOUSING_TYPE_OTHER;
        String structureGroup = isRcSrcScSteel(building.getStrctCdNm()) ? STRUCTURE_GROUP_RC : STRUCTURE_GROUP_OTHER;

        if (HOUSING_TYPE_MULTI_FAMILY.equals(housingType) && STRUCTURE_GROUP_RC.equals(structureGroup)) {
            int approvalYear = building.getUseAprvYmd().getYear();
            int clampedYear = Math.max(CURVE_YEAR_MIN, Math.min(CURVE_YEAR_MAX, approvalYear));
            String floorTier = building.getGrndNofl() != null && building.getGrndNofl() >= APARTMENT_MIN_FLOORS
                    ? FLOOR_TIER_5_UP : FLOOR_TIER_4_DOWN;
            return referenceDataCache.findCurve(housingType, structureGroup, floorTier, clampedYear)
                    .orElseThrow(() -> new IllegalStateException(
                            "aging_requirement 별표1 커브 데이터 누락: year=" + clampedYear + ", floorTier=" + floorTier));
        }
        return referenceDataCache.findFlat(housingType, structureGroup)
                .orElseThrow(() -> new IllegalStateException(
                        "aging_requirement flat 데이터 누락: housingType=" + housingType + ", structureGroup=" + structureGroup));
    }

    // F-05 §2.1/FEATURE.md §8.4 "예상 세대 증가" — 공동주택(세대 개념이 있는 유형)만 산출, 그 외
    // 유형(단독다가구/상업업무용/공장창고)은 null. F-04 §2.1-e와 같은 방식(gfa/hh_cnt = 세대당
    // 평균면적)으로 증축가능면적을 나눠 몇 세대를 더 지을 수 있는지 추정 — 소수 세대는 버림(floor).
    // public static — F-19(MeasurementServiceImpl)가 실측 증축면적으로 세대수 증가를 재계산할 때 재사용.
    public static Integer estimateAdditionalHouseholds(BuildingEntity building, BigDecimal additionalBuildableAreaSqm) {
        if (!HOUSING_TYPE_MULTI_FAMILY.equals(building.getMnUsgCdNm())) {
            return null;
        }
        Integer householdCount = building.getHhCnt();
        BigDecimal grossFloorArea = building.getGfa();
        if (householdCount == null || householdCount == 0 || grossFloorArea == null || grossFloorArea.signum() <= 0) {
            return null;
        }
        BigDecimal averageUnitArea = grossFloorArea.divide(BigDecimal.valueOf(householdCount), 4, RoundingMode.HALF_UP);
        return additionalBuildableAreaSqm.divide(averageUnitArea, 0, RoundingMode.DOWN).intValue();
    }

    private static boolean isRcSrcScSteel(String strctCdNm) {
        return strctCdNm != null && (strctCdNm.contains("철근콘크리트") || strctCdNm.contains("철골"));
    }

    private static boolean isInProgress(PermitEntity permit, int validityYears) {
        return IN_PROGRESS_BUILD_TYPES.contains(permit.getBuildType())
                && permit.getUseApprovalDate() == null
                && permit.getPermitDate() != null
                && !permit.getPermitDate().isBefore(LocalDate.now().minusYears(validityYears));
    }
}
