package com.mteam.rebuildengine.service.analysis;

import com.mteam.rebuildengine.model.entity.AgingFactorEntity;
import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.model.entity.CostBasePriceEntity;
import com.mteam.rebuildengine.model.entity.StructureIndexEntity;
import com.mteam.rebuildengine.model.entity.UsageIndexEntity;
import com.mteam.rebuildengine.model.response.CostBasisResponse;
import com.mteam.rebuildengine.model.response.CostEstimationResponse;
import com.mteam.rebuildengine.model.response.CostEstimationStatus;
import com.mteam.rebuildengine.model.response.RemodelingResultResponse;
import com.mteam.rebuildengine.model.response.RemodelingVerdict;
import com.mteam.rebuildengine.repository.BuildingRepository;
import com.mteam.rebuildengine.utils.PropertyType;
import com.mteam.rebuildengine.utils.PropertyTypeClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// FEATURE_07_COST.md §3.2 — 국세청고시 제2024-38호 제6~8조(기준단가)+제10조(잔가율) 산식을 그대로
// 구현한다. 법령 수치는 참조 테이블(cost_base_price/structure_index/usage_index/aging_factor)에서
// 조회하고 코드에 하드코딩하지 않는다(F-06 LAW-001과 동일 원칙). F-06(`RemodelingService`)의
// buildingAgeYears·verdict를 그대로 재사용 — 노후도를 새로 계산하지 않는다(§5.2).
@Service
@RequiredArgsConstructor
public class CostServiceImpl implements CostService {

    private static final Set<String> RC_NAMES = Set.of("철근콘크리트구조", "프리케스트콘크리트구조", "기타콘크리트구조");
    private static final Set<String> SRC_NAMES = Set.of("철골철근콘크리트구조");
    private static final Set<String> SC_NAMES =
            Set.of("일반철골구조", "경량철골구조", "철골콘크리트구조", "기타강구조", "강파이프구조");
    private static final String STRUCTURE_RC = "RC";
    private static final String STRUCTURE_SRC = "SRC";
    private static final String STRUCTURE_SC = "SC";
    private static final String STRUCTURE_ETC = "ETC";

    // §3.2 — PropertyTypeClassifier 결과(6종)를 usage_index.code(5종)로 변환. ROW_HOUSE·SINGLE_FAMILY는
    // 고시상 같은 "기타 주거용"이라 MULTI 하나로 합쳐진다.
    private static final Map<PropertyType, String> USAGE_CODE_BY_PROPERTY_TYPE = Map.of(
            PropertyType.APARTMENT, "APT",
            PropertyType.ROW_HOUSE, "MULTI",
            PropertyType.SINGLE_FAMILY, "MULTI",
            PropertyType.OFFICETEL, "OFFICETEL",
            PropertyType.COMMERCIAL, "COMMERCIAL",
            PropertyType.INDUSTRIAL, "FACTORY"
    );

    // §3.2 item 4 — R(최종잔존가치율)은 전 구조군 공통 고정값이라 aging_factor처럼 구조별로 나뉘지
    // 않는다. base_price/구조지수/용도지수/k와 달리 참조 테이블화하지 않은 유일한 법령 수치.
    private static final BigDecimal RESIDUAL_VALUE_RATE = BigDecimal.valueOf(0.1);
    private static final int CALC_SCALE = 10;

    private final BuildingRepository buildingRepository;
    private final RemodelingService remodelingService;
    private final ReferenceDataCache referenceDataCache;

    @Override
    public Optional<CostEstimationResponse> getCostEstimation(String buildingId) {
        return buildingRepository.findByBdrgSnAndIsAncillaryFalseAndIsOutOfScopeFalseAndIsDeletedFalse(buildingId).map(building -> {
            RemodelingResultResponse remodeling = remodelingService.getRemodelingResult(building.getBdrgSn())
                    .orElseThrow(() -> new IllegalStateException("RemodelingService 결과 누락: " + building.getBdrgSn()));
            return evaluate(building, remodeling);
        });
    }

    @Override
    public CostEstimationResponse getCostEstimation(BuildingEntity building, RemodelingResultResponse remodeling) {
        return evaluate(building, remodeling);
    }

    private CostEstimationResponse evaluate(BuildingEntity building, RemodelingResultResponse remodeling) {
        // §3.3 — F-06 "불가" 판정 건물은 공사비 산정 대상 아님(점수와 무관하게 게이트 우선).
        if (remodeling.verdict() == RemodelingVerdict.NOT_POSSIBLE) {
            return CostEstimationResponse.notApplicable(CostEstimationStatus.NOT_APPLICABLE_REMODELING_NOT_POSSIBLE);
        }

        BigDecimal grossFloorArea = building.getGfa();
        Integer buildingAgeYears = remodeling.basis().buildingAgeYears();
        // 연면적 또는 노후도(사용승인일 미확보) 둘 중 하나라도 없으면 산출 불가 — 둘 다 "필수 입력값
        // 없음"이라 같은 상태로 취급(§3.3 "연면적 데이터 없음" 행 재사용).
        if (grossFloorArea == null || grossFloorArea.signum() <= 0 || buildingAgeYears == null) {
            return CostEstimationResponse.notApplicable(CostEstimationStatus.AREA_UNAVAILABLE);
        }

        Optional<PropertyType> propertyType =
                PropertyTypeClassifier.classify(building.getMnUsgCdNm(), building.getGrndNofl());
        Optional<UsageIndexEntity> usageIndex = propertyType.map(USAGE_CODE_BY_PROPERTY_TYPE::get)
                .flatMap(referenceDataCache::usageIndex);
        if (usageIndex.isEmpty()) {
            return CostEstimationResponse.notApplicable(CostEstimationStatus.NO_REFERENCE_RATE);
        }

        String structureGroup = classifyStructureGroup(building.getStrctCdNm());
        StructureIndexEntity structureIndex = referenceDataCache.structureIndex(structureGroup);
        AgingFactorEntity agingFactor = referenceDataCache.agingFactor(structureGroup);
        CostBasePriceEntity basePrice = referenceDataCache.costBasePrice(LocalDate.now());

        BigDecimal baseUnitPrice = basePrice.getBasePrice()
                .multiply(BigDecimal.valueOf(structureIndex.getIndex())).divide(BigDecimal.valueOf(100))
                .multiply(BigDecimal.valueOf(usageIndex.get().getIndex())).divide(BigDecimal.valueOf(100));

        BigDecimal rn = residualRate(buildingAgeYears, structureIndex.getLifeYear());
        BigDecimal factorMin = agingFactorValue(rn, agingFactor.getMinFactor());
        BigDecimal factorMax = agingFactorValue(rn, agingFactor.getMaxFactor());
        // factorDefault도 factorMin/Max와 같은 변환(1+(1-Rn)×k)을 거쳐야 셋이 같은 스케일의 "최종
        // 배율"이 된다 — aging_factor.default_factor는 raw k값이라 그대로 노출하면(2026-08-08~
        // 2026-08-10 버그) min/max보다 훨씬 작은 값이 되어 소비 측에서 그대로 곱하면 "기준" 비용이
        // "최소" 비용보다 작아지는 계산 오류가 난다(FEATURE.md §8.16 2026-08-12 재현 사례).
        BigDecimal factorDefault = agingFactorValue(rn, agingFactor.getDefaultFactor());

        BigDecimal minCost = grossFloorArea.multiply(baseUnitPrice).multiply(factorMin).setScale(0, RoundingMode.HALF_UP);
        BigDecimal maxCost = grossFloorArea.multiply(baseUnitPrice).multiply(factorMax).setScale(0, RoundingMode.HALF_UP);

        CostBasisResponse basis = new CostBasisResponse(
                grossFloorArea, building.getStrctCdNm(), propertyType.get().label(),
                baseUnitPrice.setScale(0, RoundingMode.HALF_UP), buildingAgeYears, factorMin, factorMax,
                factorDefault, basePrice.getSource()
        );
        return new CostEstimationResponse(minCost, maxCost, CostEstimationStatus.AVAILABLE, basis);
    }

    // Rn = 1 - (1-R) * n/N, 0~1로 클램프(내용연수 초과 건물의 잔가율이 음수로 내려가지 않도록).
    private static BigDecimal residualRate(int buildingAgeYears, int lifeYear) {
        BigDecimal decline = BigDecimal.ONE.subtract(RESIDUAL_VALUE_RATE)
                .multiply(BigDecimal.valueOf(buildingAgeYears))
                .divide(BigDecimal.valueOf(lifeYear), CALC_SCALE, RoundingMode.HALF_UP);
        BigDecimal rn = BigDecimal.ONE.subtract(decline);
        return rn.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    // 노후도 보정계수 = 1 + (1-Rn) * k
    private static BigDecimal agingFactorValue(BigDecimal rn, BigDecimal k) {
        return BigDecimal.ONE.add(BigDecimal.ONE.subtract(rn).multiply(k));
    }

    // FEATURE_07_COST.md §3.2 item 2 — STRCT_CD_NM 실측값을 4개 구조군으로 매핑(backend 확정, §5.1).
    private static String classifyStructureGroup(String strctCdNm) {
        if (strctCdNm == null) {
            return STRUCTURE_ETC;
        }
        if (SRC_NAMES.contains(strctCdNm)) {
            return STRUCTURE_SRC;
        }
        if (RC_NAMES.contains(strctCdNm)) {
            return STRUCTURE_RC;
        }
        if (SC_NAMES.contains(strctCdNm)) {
            return STRUCTURE_SC;
        }
        return STRUCTURE_ETC;
    }
}
