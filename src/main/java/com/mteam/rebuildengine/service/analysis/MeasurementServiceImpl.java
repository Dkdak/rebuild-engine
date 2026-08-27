package com.mteam.rebuildengine.service.analysis;

import com.mteam.rebuildengine.exception.InvalidCredentialsException;
import com.mteam.rebuildengine.model.entity.AcquisitionEntityType;
import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.model.entity.MeasurementEntity;
import com.mteam.rebuildengine.model.entity.MeasurementHistoryEntity;
import com.mteam.rebuildengine.model.entity.MeasurementItem;
import com.mteam.rebuildengine.model.entity.MeasurementValidityEntity;
import com.mteam.rebuildengine.model.entity.SiteCondition;
import com.mteam.rebuildengine.model.entity.ValidityAnchor;
import com.mteam.rebuildengine.model.entity.ZoningLimitEntity;
import com.mteam.rebuildengine.model.request.MeasurementStepSaveRequest;
import com.mteam.rebuildengine.model.response.BuildingReportResponse;
import com.mteam.rebuildengine.model.response.CostEstimationResponse;
import com.mteam.rebuildengine.model.response.CostEstimationStatus;
import com.mteam.rebuildengine.model.response.ConfidenceLevel;
import com.mteam.rebuildengine.model.response.LimitationResolutionResponse;
import com.mteam.rebuildengine.model.response.MarketAnalysisResponse;
import com.mteam.rebuildengine.model.response.MeasurementDetailResponse;
import com.mteam.rebuildengine.model.response.MeasurementHistoryEntryResponse;
import com.mteam.rebuildengine.model.response.MeasurementItemStatus;
import com.mteam.rebuildengine.model.response.MeasurementItemStatusResponse;
import com.mteam.rebuildengine.model.response.MeasurementListItemResponse;
import com.mteam.rebuildengine.model.response.MeasurementProgressResponse;
import com.mteam.rebuildengine.model.response.MeasurementRecalculationResponse;
import com.mteam.rebuildengine.model.response.MeasurementStepSaveResponse;
import com.mteam.rebuildengine.model.response.PricePositionResponse;
import com.mteam.rebuildengine.model.response.RemodelingBasisResponse;
import com.mteam.rebuildengine.model.response.RemodelingResultResponse;
import com.mteam.rebuildengine.model.response.RemodelingVerdict;
import com.mteam.rebuildengine.model.response.SafetyInspectionResponse;
import com.mteam.rebuildengine.model.response.ValuedField;
import com.mteam.rebuildengine.model.response.ZoningLimitResponse;
import com.mteam.rebuildengine.repository.BuildingRepository;
import com.mteam.rebuildengine.repository.InvestmentResultRepository;
import com.mteam.rebuildengine.repository.MeasurementHistoryRepository;
import com.mteam.rebuildengine.repository.MeasurementRepository;
import com.mteam.rebuildengine.repository.MeasurementValidityRepository;
import com.mteam.rebuildengine.repository.UserRepository;
import com.mteam.rebuildengine.utils.AcquisitionCostCalculator;
import com.mteam.rebuildengine.utils.PropertyType;
import com.mteam.rebuildengine.utils.PropertyTypeClassifier;
import com.mteam.rebuildengine.utils.RepresentativePriceCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// FEATURE_19_PERSONALIZED_ANALYSIS.md §3 — F-19 실측 입력. 계산 엔진은 F-06/07/08/09를 그대로 재사용
// 하고 입력 소스만 바꿔치기한다(§3.3) — 여기서 새로 계산 로직을 만들지 않는다.
// 취득 부대비용(AcquisitionCostCalculator)은 아직 acquisitionEntityType을 반영하지 않는다 —
// 다주택·법인 중과세율이 조정대상지역 여부로 갈리는 규정이라 LAW-003 보강(content 역할) 없이는
// 세율을 확정할 수 없다(2026-08-24 product 전달, DOMAIN.md §4).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeasurementServiceImpl implements MeasurementService {

    // LAW-003_취득세_중개보수_요율.md — 취득세법상 "주택" 특례세율 대상(InvestmentServiceImpl과 동일 분류).
    private static final Set<PropertyType> HOUSING_TYPES_FOR_ACQUISITION_TAX =
            Set.of(PropertyType.APARTMENT, PropertyType.ROW_HOUSE, PropertyType.SINGLE_FAMILY);

    // §2.3-b "ROI를 직접 만드는 값 4개" — progress/status/nextInputField 판정 기준.
    private static final List<MeasurementItem> CORE_ITEMS = List.of(
            MeasurementItem.EXPANDABLE_AREA, MeasurementItem.ESTIMATE,
            MeasurementItem.PURCHASE_PRICE, MeasurementItem.FUTURE_VALUE);

    private static final Set<MeasurementItem> STEP1_ITEMS =
            EnumSet.of(MeasurementItem.ZONING, MeasurementItem.HEIGHT_LIMIT, MeasurementItem.SAFETY);

    private static final BigDecimal WON_PER_10K = BigDecimal.valueOf(10_000);

    private final MeasurementRepository measurementRepository;
    private final MeasurementHistoryRepository measurementHistoryRepository;
    private final MeasurementValidityRepository measurementValidityRepository;
    private final UserRepository userRepository;
    private final BuildingRepository buildingRepository;
    private final InvestmentResultRepository investmentResultRepository;
    private final RemodelingService remodelingService;
    private final CostService costService;
    private final MarketService marketService;
    private final ReferenceDataCache referenceDataCache;
    private final ObjectMapper objectMapper;

    @Override
    public List<ZoningLimitResponse> listZoningLimits() {
        return referenceDataCache.allZoningLimits().stream()
                .map(z -> new ZoningLimitResponse(z.getZoneName(), z.getFloorAreaRatioLimit(), z.getCoverageRatioLimit()))
                .sorted(Comparator.comparing(ZoningLimitResponse::floorAreaRatioLimit))
                .toList();
    }

    @Override
    public List<MeasurementListItemResponse> list(String email) {
        Long userId = resolveUserId(email);
        Map<String, ValidityRule> validity = loadValidity();
        return measurementRepository.findByUserIdAndIsDeletedFalse(userId).stream()
                .map(m -> toListItem(m, validity))
                .toList();
    }

    private MeasurementListItemResponse toListItem(MeasurementEntity m, Map<String, ValidityRule> validity) {
        MeasurementProgressResponse progress = coreProgress(m);
        String status = progress.measured() == progress.total() ? "COMPLETED" : "IN_PROGRESS";
        String nextInputField = nextInputField(m);
        int recheckCount = (int) computeItemStatuses(m, validity).stream()
                .filter(s -> s.status() == MeasurementItemStatus.RECHECK).count();
        Optional<BuildingEntity> building = buildingRepository
                .findByBdrgSnAndIsAncillaryFalseAndIsOutOfScopeFalseAndIsDeletedFalse(m.getBuildingId());
        // F-11 §3.1 property:null 패턴과 동일 — 건물이 배치에서 소프트 삭제돼도 목록에서 지우지 않는다.
        if (building.isEmpty()) {
            return new MeasurementListItemResponse(m.getBuildingId(), null, progress, status, null, nextInputField, recheckCount);
        }
        BigDecimal measuredRoi = computeRecalculation(building.get(), m).roi();
        return new MeasurementListItemResponse(m.getBuildingId(), building.get().getPlatPlc(), progress, status, measuredRoi,
                nextInputField, recheckCount);
    }

    // §3.2-b(2026-08-27, product 확정) — 실측 레코드가 없어도 건물이 있으면 200으로 공공데이터
    // 추정치를 내려준다(레코드는 저장하지 않는다, 조회는 계산만 한다). "건물은 있는데 실측이 없다"는
    // 정상 상태라 빈 화면을 만들지 않는다(§2.2-b 규칙 2). 404는 건물 자체가 없을 때만 — 실측도
    // 건물도 둘 다 없을 때뿐이다.
    @Override
    public Optional<MeasurementDetailResponse> get(String email, String buildingId) {
        Long userId = resolveUserId(email);
        Optional<MeasurementEntity> measurementOpt =
                measurementRepository.findByUserIdAndBuildingIdAndIsDeletedFalse(userId, buildingId);
        Optional<BuildingEntity> buildingOpt = buildingRepository
                .findByBdrgSnAndIsAncillaryFalseAndIsOutOfScopeFalseAndIsDeletedFalse(buildingId);
        if (measurementOpt.isEmpty() && buildingOpt.isEmpty()) {
            return Optional.empty();
        }

        Map<String, ValidityRule> validity = loadValidity();
        MeasurementEntity m = measurementOpt.orElseGet(() ->
                MeasurementEntity.builder().userId(userId).buildingId(buildingId).build());
        // 건물 자체가 배치에서 소프트 삭제된 드문 경우(실측 레코드는 있음) — isHousing을 판정할 근거
        // (building)가 아예 없어 false로 둔다(실제 유형 판정이 아니라 안전한 기본값).
        MeasurementRecalculationResponse recalculation = buildingOpt
                .map(building -> computeRecalculation(building, m))
                .orElseGet(() -> MeasurementRecalculationResponse.unavailable(false));
        return Optional.of(new MeasurementDetailResponse(buildingId, toValues(m),
                computeItemStatuses(m, validity), recalculation, coreProgress(m)));
    }

    @Override
    @Transactional
    public MeasurementStepSaveResponse saveStep(String email, String buildingId, int stepNo, MeasurementStepSaveRequest request) {
        if (stepNo < 1 || stepNo > 5) {
            throw new IllegalArgumentException("stepNo는 1~5여야 합니다: " + stepNo);
        }
        Long userId = resolveUserId(email);
        BuildingEntity building = buildingRepository
                .findByBdrgSnAndIsAncillaryFalseAndIsOutOfScopeFalseAndIsDeletedFalse(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 건물입니다: " + buildingId));

        MeasurementEntity measurement = measurementRepository.findByUserIdAndBuildingId(userId, buildingId)
                .map(existing -> {
                    existing.reactivate();
                    return existing;
                })
                .orElseGet(() -> measurementRepository.save(
                        MeasurementEntity.builder().userId(userId).buildingId(buildingId).build()));

        Map<String, ValidityRule> validity = loadValidity();
        Set<String> recheckBefore = computeItemStatuses(measurement, validity).stream()
                .filter(s -> s.status() == MeasurementItemStatus.RECHECK)
                .map(MeasurementItemStatusResponse::itemKey)
                .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        List<PendingChange> pendingChanges = switch (stepNo) {
            case 1 -> saveStep1(measurement, request, now);
            case 2 -> saveStep2(measurement, request, now);
            case 3 -> saveStep3(measurement, request, now);
            case 4 -> saveStep4(measurement, request, now);
            default -> saveStep5(measurement, request, now);
        };

        MeasurementRecalculationResponse recalculation = computeRecalculation(building, measurement);

        for (PendingChange change : pendingChanges) {
            measurementHistoryRepository.save(MeasurementHistoryEntity.builder()
                    .userId(userId).buildingId(buildingId)
                    .stepNo(change.item().stepNo()).itemKey(change.item().name())
                    .previousValue(change.previousJson()).newValue(change.newJson())
                    .measuredRoiAtChange(recalculation.roi())
                    .build());
        }

        List<MeasurementItemStatusResponse> itemStatuses = computeItemStatuses(measurement, validity);
        List<String> recheckTriggered = itemStatuses.stream()
                .filter(s -> s.status() == MeasurementItemStatus.RECHECK && !recheckBefore.contains(s.itemKey()))
                .map(MeasurementItemStatusResponse::itemKey)
                .toList();

        return new MeasurementStepSaveResponse(recalculation, itemStatuses, recheckTriggered, coreProgress(measurement));
    }

    @Override
    public List<MeasurementHistoryEntryResponse> history(String email, String buildingId) {
        Long userId = resolveUserId(email);
        return measurementHistoryRepository.findByUserIdAndBuildingIdOrderByChangedAtDesc(userId, buildingId).stream()
                .map(h -> new MeasurementHistoryEntryResponse(h.getChangedAt(), h.getStepNo(), h.getItemKey(),
                        h.getPreviousValue(), h.getNewValue(), h.getMeasuredRoiAtChange()))
                .toList();
    }

    @Override
    @Transactional
    public void delete(String email, String buildingId) {
        Long userId = resolveUserId(email);
        measurementRepository.findByUserIdAndBuildingIdAndIsDeletedFalse(userId, buildingId)
                .ifPresent(MeasurementEntity::markDeleted);
    }

    // FEATURE_19 §1.1 — CASE1/CASE2를 분기하는 대신 응답 하나로 통일한다. 활성 실측이 없으면 빈
    // MeasurementEntity(전부 null)로 재계산해 CASE1과 동일한 결과(measured 전부 false)를 만든다 —
    // computeRecalculation을 그대로 재사용해 계산 로직 이중화를 피한다.
    @Override
    public BuildingReportResponse getReport(String email, String buildingId) {
        Long userId = resolveUserId(email);
        BuildingEntity building = buildingRepository
                .findByBdrgSnAndIsAncillaryFalseAndIsOutOfScopeFalseAndIsDeletedFalse(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 건물입니다: " + buildingId));

        Optional<MeasurementEntity> measurementOpt = measurementRepository.findByUserIdAndBuildingIdAndIsDeletedFalse(userId, buildingId);
        boolean caseTwo = measurementOpt.isPresent();
        MeasurementEntity m = measurementOpt.orElseGet(() -> MeasurementEntity.builder().userId(userId).buildingId(buildingId).build());

        String grade = investmentResultRepository.findById(buildingId)
                .filter(e -> !e.isDeleted())
                .map(e -> e.getGrade().getDisplayName())
                .orElse(null);

        MeasurementRecalculationResponse recalc = computeRecalculation(building, m);

        // 05 "증축 여력" 세대수 — computeRecalculation이 이미 면적 자체는 계산해 두므로(additionalBuildableAreaSqm)
        // 세대수만 여기서 보충한다(세대기반 유형 전용, 그 외는 항상 null). computeRecalculation과 같은
        // STEP1 용적률 상한 보정을 여기서도 적용해야 세대수가 additionalBuildableAreaSqm과 같은 기준을 쓴다.
        Optional<RemodelingResultResponse> remodelingOpt = remodelingService.getRemodelingResult(buildingId)
                .map(r -> m.getFarLimitPct() != null ? withMeasuredFarLimit(r, building, m.getZoneName(), m.getFarLimitPct()) : r);
        boolean areaMeasured = m.getActualExpandableAreaSqm() != null;
        Integer additionalHouseholds = remodelingOpt.map(r -> areaMeasured
                ? RemodelingServiceImpl.estimateAdditionalHouseholds(building, m.getActualExpandableAreaSqm())
                : r.basis().estimatedAdditionalHouseholds()).orElse(null);

        return new BuildingReportResponse(
                caseTwo, grade,
                new ValuedField<>(recalc.totalInvestment(), recalc.totalInvestmentMeasured()),
                new ValuedField<>(recalc.projectedValue(), recalc.projectedValueMeasured()),
                new ValuedField<>(recalc.expectedProfit(), recalc.projectedValueMeasured() || recalc.totalInvestmentMeasured()),
                new ValuedField<>(recalc.roi(), recalc.projectedValueMeasured() || recalc.totalInvestmentMeasured()),
                recalc.additionalBuildableAreaSqm(),
                additionalHouseholds,
                pricePosition(building, m, remodelingOpt),
                buildLimitations(m));
    }

    // FEATURE_10_MARKET.md CASE2(2026-08-27) — 매입가가 실측이면 그 값 기준으로 순위를 다시 매기고,
    // 실측이 없거나(비주택 유형·모집단 자체가 없어 등) 계산이 안 되면 공공데이터 기준(recentTrade→중앙값
    // 폴백, 기존 §8.17 동작)으로 내려간다. §2.2-b 규칙 2 그대로 — 빈 값 대신 항상 최선의 추정치를 준다.
    private ValuedField<PricePositionResponse> pricePosition(BuildingEntity building, MeasurementEntity m,
                                                                Optional<RemodelingResultResponse> remodelingOpt) {
        if (m.getActualPurchasePrice() != null) {
            Optional<PricePositionResponse> measured = marketService.getMeasuredPricePosition(building, m.getActualPurchasePrice());
            if (measured.isPresent()) {
                return new ValuedField<>(measured.get(), true);
            }
        }
        PricePositionResponse publicPosition = remodelingOpt
                .map(r -> marketService.getMarketAnalysis(building, r).pricePosition())
                .orElse(null);
        return new ValuedField<>(publicPosition, false);
    }

    // FEATURE_10_REFERENCE.md "08 분석의 한계" 5개 문장의 해소 현황(§1.1) — 각 문장이 가리키는 F-19
    // 항목이 계산 반영이면 전부 채워졌을 때 RESOLVED, 기록 전용이면 채워져도 RECORDED(값은 안 바뀜),
    // 대응 항목이 아예 없으면(세금·금융·보유비용) 항상 UNRESOLVED — F-19 범위 밖(§2.2 확정).
    private List<LimitationResolutionResponse> buildLimitations(MeasurementEntity m) {
        return List.of(
                new LimitationResolutionResponse(1,
                        "실제 건물의 현장 상태(구조·설비·마감·배관 등)는 반영되지 않았습니다.",
                        recordedStatus(m.getEstimateSiteCondition() != null || m.getEstimateSiteNote() != null)),
                new LimitationResolutionResponse(2,
                        "등기부 권리관계, 임대차·명도 조건은 분석 범위에 포함되지 않았습니다.",
                        recordedStatus(m.getRegistryRightsStatus() != null, m.getLeaseVacancyCondition() != null)),
                new LimitationResolutionResponse(3,
                        "실제 공사 견적·설계비·인허가 비용은 시장 상황에 따라 달라질 수 있습니다.",
                        resolvedStatus(m.getActualConstructionEstimate() != null,
                                m.getDesignFee() != null || m.getPermitFee() != null)),
                new LimitationResolutionResponse(4,
                        "인허가 승인 가능 여부, 규제 해석·완화 여부는 관할 행정기관 확인이 필요합니다.",
                        resolvedStatus(m.getZoneName() != null || m.getFarLimitPct() != null,
                                m.getHeightLimit() != null || m.getDistrictPlan() != null,
                                m.getSafetyInspection() != null)),
                // §2.3-d(2026-08-24 확정) — "미해소"는 "입력하면 풀린다"는 뜻인데, 세금·금융·보유비용은
                // F-19가 다루지 않기로 한 항목(§2.2)이라 사용자가 뭘 입력해도 안 풀린다. UNRESOLVED로
                // 두면 영원히 안 끝나는 숙제로 보여서 별도 상태(OUT_OF_SCOPE)로 고정한다.
                new LimitationResolutionResponse(5,
                        "세금·금융비용·보유기간 중 유지관리비는 일부 또는 전부 반영되지 않았습니다.",
                        "OUT_OF_SCOPE"));
    }

    // 계산 반영 항목용 — 전부 참이면 RESOLVED, 일부만 참이면 PARTIAL, 전부 거짓이면 UNRESOLVED.
    private static String resolvedStatus(boolean... filled) {
        long trueCount = 0;
        for (boolean f : filled) {
            if (f) {
                trueCount++;
            }
        }
        if (trueCount == filled.length) {
            return "RESOLVED";
        }
        return trueCount > 0 ? "PARTIAL" : "UNRESOLVED";
    }

    // 기록 전용 항목용 — 채워져도 계산엔 안 반영되므로 최댓값이 RECORDED(해소 아님).
    private static String recordedStatus(boolean... filled) {
        long trueCount = 0;
        for (boolean f : filled) {
            if (f) {
                trueCount++;
            }
        }
        if (trueCount == 0) {
            return "UNRESOLVED";
        }
        return trueCount == filled.length ? "RECORDED" : "PARTIAL";
    }

    // ── STEP별 저장 — 값이 바뀐 항목만 PendingChange로 모아 saveStep()이 이력에 남긴다. 값 자체는
    // 바뀌었든 아니든 항상 입력 시각을 갱신한다(§2.2-b "재확인 후 그대로 저장"도 갱신일을 새로 찍음).
    private List<PendingChange> saveStep1(MeasurementEntity m, MeasurementStepSaveRequest r, LocalDateTime now) {
        List<PendingChange> changes = new ArrayList<>();

        ZoningValue zoningOld = new ZoningValue(m.getZoneName(), m.getFarLimitPct());
        m.updateZoning(r.zoneName(), resolveFarLimitPct(r), now);
        addIfChanged(changes, MeasurementItem.ZONING, zoningOld, new ZoningValue(m.getZoneName(), m.getFarLimitPct()));

        HeightValue heightOld = new HeightValue(m.getHeightLimit(), m.getDistrictPlan());
        m.updateHeightLimit(r.heightLimit(), r.districtPlan(), now);
        addIfChanged(changes, MeasurementItem.HEIGHT_LIMIT, heightOld, new HeightValue(m.getHeightLimit(), m.getDistrictPlan()));

        String safetyOldJson = m.getSafetyInspection();
        boolean safetyAllNull = r.safetyGrade() == null && r.safetyInspectionDate() == null && r.safetyAllowedExpansionType() == null;
        String safetyNewJson = safetyAllNull ? null
                : writeJson(new SafetyInspectionResponse(r.safetyGrade(), r.safetyInspectionDate(), r.safetyAllowedExpansionType()));
        m.updateSafety(safetyNewJson, now);
        if (!Objects.equals(safetyOldJson, safetyNewJson)) {
            changes.add(new PendingChange(MeasurementItem.SAFETY, safetyOldJson, safetyNewJson));
        }
        return changes;
    }

    // §2.2-e(FEATURE_19 확정) — 용적률 상한은 원칙적으로 파생값이다: 용도지역을 zoning_limit(F-06과
    // 같은 테이블, `ReferenceDataCache`도 같이 재사용해 시드 재조회를 안 만든다)에서 정확히 일치하는
    // zone_name으로 찾아 법정 상한을 그대로 쓴다. "지구단위계획으로 상한이 조례와 달라지는 경우는
    // 높이제한·지구단위계획 입력이 그것을 덮는다"(§2.2) — 그 예외 하나만 클라이언트가 farLimitPct를
    // 직접 실어 보내는 것으로 표현한다(비어있지 않으면 override로 인정, 파생을 건너뛴다). zoneName이
    // zoning_limit에 없으면(개발제한구역·미지정 등 16종 밖) 파생 불가 — null로 남는다.
    private BigDecimal resolveFarLimitPct(MeasurementStepSaveRequest r) {
        if (r.farLimitPct() != null) {
            return r.farLimitPct();
        }
        if (r.zoneName() == null) {
            return null;
        }
        return referenceDataCache.zoningLimit(r.zoneName()).map(ZoningLimitEntity::getFloorAreaRatioLimit).orElse(null);
    }

    private List<PendingChange> saveStep2(MeasurementEntity m, MeasurementStepSaveRequest r, LocalDateTime now) {
        List<PendingChange> changes = new ArrayList<>();
        AreaValue areaOld = new AreaValue(m.getActualExpandableAreaSqm(), m.getExpandableAreaDocumentDate());
        m.updateExpandableArea(r.actualExpandableAreaSqm(), r.expandableAreaDocumentDate(), now);
        addIfChanged(changes, MeasurementItem.EXPANDABLE_AREA, areaOld,
                new AreaValue(m.getActualExpandableAreaSqm(), m.getExpandableAreaDocumentDate()));

        String reasonOld = m.getReductionReason();
        m.updateReductionReason(r.reductionReason(), now);
        addIfChanged(changes, MeasurementItem.REDUCTION_REASON, reasonOld, m.getReductionReason());
        return changes;
    }

    private List<PendingChange> saveStep3(MeasurementEntity m, MeasurementStepSaveRequest r, LocalDateTime now) {
        List<PendingChange> changes = new ArrayList<>();
        EstimateValue estimateOld = new EstimateValue(m.getActualConstructionEstimate(), m.getEstimateDocumentDate());
        m.updateEstimate(r.actualConstructionEstimate(), r.estimateDocumentDate(), now);
        addIfChanged(changes, MeasurementItem.ESTIMATE, estimateOld,
                new EstimateValue(m.getActualConstructionEstimate(), m.getEstimateDocumentDate()));

        EstimateBasisValue basisOld = new EstimateBasisValue(m.getEstimateSiteCondition(), m.getEstimateSiteNote(), m.getEstimateSource());
        m.updateEstimateBasis(r.estimateSiteCondition(), r.estimateSiteNote(), r.estimateSource(), now);
        addIfChanged(changes, MeasurementItem.ESTIMATE_BASIS, basisOld,
                new EstimateBasisValue(m.getEstimateSiteCondition(), m.getEstimateSiteNote(), m.getEstimateSource()));

        DesignPermitFeeValue feeOld = new DesignPermitFeeValue(m.getDesignFee(), m.getPermitFee());
        m.updateDesignPermitFee(r.designFee(), r.permitFee(), now);
        addIfChanged(changes, MeasurementItem.DESIGN_PERMIT_FEE, feeOld, new DesignPermitFeeValue(m.getDesignFee(), m.getPermitFee()));
        return changes;
    }

    private List<PendingChange> saveStep4(MeasurementEntity m, MeasurementStepSaveRequest r, LocalDateTime now) {
        List<PendingChange> changes = new ArrayList<>();
        BigDecimal priceOld = m.getActualPurchasePrice();
        m.updatePurchasePrice(r.actualPurchasePrice(), now);
        addIfChanged(changes, MeasurementItem.PURCHASE_PRICE, priceOld, m.getActualPurchasePrice());

        AcquisitionEntityType entityOld = m.getAcquisitionEntityType();
        m.updateAcquisitionEntity(r.acquisitionEntityType(), now);
        addIfChanged(changes, MeasurementItem.ACQUISITION_ENTITY, entityOld, m.getAcquisitionEntityType());

        String registryOld = m.getRegistryRightsStatus();
        m.updateRegistryRights(r.registryRightsStatus(), now);
        addIfChanged(changes, MeasurementItem.REGISTRY_RIGHTS, registryOld, m.getRegistryRightsStatus());

        String leaseOld = m.getLeaseVacancyCondition();
        m.updateLeaseVacancy(r.leaseVacancyCondition(), now);
        addIfChanged(changes, MeasurementItem.LEASE_VACANCY, leaseOld, m.getLeaseVacancyCondition());
        return changes;
    }

    private List<PendingChange> saveStep5(MeasurementEntity m, MeasurementStepSaveRequest r, LocalDateTime now) {
        List<PendingChange> changes = new ArrayList<>();
        BigDecimal priceOld = m.getPostRemodelEstimatedPrice();
        m.updateFutureValue(r.postRemodelEstimatedPrice(), now);
        addIfChanged(changes, MeasurementItem.FUTURE_VALUE, priceOld, m.getPostRemodelEstimatedPrice());

        String memoOld = m.getValuationBasisMemo();
        m.updateFutureValueBasis(r.valuationBasisMemo(), now);
        addIfChanged(changes, MeasurementItem.FUTURE_VALUE_BASIS, memoOld, m.getValuationBasisMemo());
        return changes;
    }

    private <T> void addIfChanged(List<PendingChange> changes, MeasurementItem item, T oldValue, T newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.add(new PendingChange(item, writeJson(oldValue), writeJson(newValue)));
        }
    }

    private record PendingChange(MeasurementItem item, String previousJson, String newJson) {
    }

    private record ZoningValue(String zoneName, BigDecimal farLimitPct) {
    }

    private record HeightValue(String heightLimit, String districtPlan) {
    }

    private record AreaValue(BigDecimal areaSqm, LocalDate documentDate) {
    }

    private record EstimateValue(BigDecimal amount, LocalDate documentDate) {
    }

    private record EstimateBasisValue(SiteCondition siteCondition, String siteNote, String source) {
    }

    private record DesignPermitFeeValue(BigDecimal designFee, BigDecimal permitFee) {
    }

    // ── 재계산 — F-06/07/08/09 계산 엔진은 그대로 두고, 4대 핵심값만 실측이 있으면 그걸로 바꿔치기한다
    // (§3.3 "입력 소스만 바꿔치기"). 등급은 F-19에서 다시 매기지 않는다(product 확정, 공공데이터 기준 유지).
    private MeasurementRecalculationResponse computeRecalculation(BuildingEntity building, MeasurementEntity m) {
        // LAW-003 §1-a(2026-08-24 확정) — 취득세 중과(지방세법 제13조의2)는 "주택"만 대상이라, 이
        // 판정(isHousing)은 remodeling/cost/market 조회와 무관하게 building만으로 먼저 정해둔다 —
        // 그래야 아래 어느 unavailable() 분기에서도 프론트에 "이 매물이 주택인지"를 내려줄 수 있다
        // (product 요청 — STEP4 취득 주체 항목을 유형별로 화면에서 숨기는 데 이 값이 필요).
        Optional<PropertyType> type = PropertyTypeClassifier.classify(building.getMnUsgCdNm(), building.getGrndNofl());
        boolean isHousing = type.isPresent() && HOUSING_TYPES_FOR_ACQUISITION_TAX.contains(type.get());
        // householdCountMissing(2026-08-27, product 요청) — building만으로 정해지는 값이라 isHousing과
        // 같은 이유로 미리 뽑아둔다. 세대기반 유형(아파트·연립다세대)인데 hh_cnt가 대장에 없으면 매입가·
        // 미래가치의 세대기반 계산이 막힌다(§2.3-f) — 증축면적·공사비는 이 값과 무관하니 영향 없음.
        boolean householdBased = type.isPresent() && (type.get() == PropertyType.APARTMENT || type.get() == PropertyType.ROW_HOUSE);
        boolean householdCountMissing = householdBased && building.getHhCnt() == null;

        Optional<RemodelingResultResponse> remodelingOpt = remodelingService.getRemodelingResult(building.getBdrgSn());
        if (remodelingOpt.isEmpty()) {
            return MeasurementRecalculationResponse.unavailable(isHousing, null, null, householdCountMissing);
        }
        RemodelingResultResponse remodeling = remodelingOpt.get();
        // verdict/verdictReason(2026-08-27, product "B" 확정) — NOT_POSSIBLE이어도 계산 자체는 막지
        // 않는다(§2.2-b 규칙 2). 원본 remodeling에서 먼저 뽑아둔다 — 아래 STEP1/2 실측 substitution은
        // buildingAgeYears/gatePassed/permitInProgress를 안 건드리므로 이 시점 값이 끝까지 유효하다.
        RemodelingVerdict verdict = remodeling.verdict();
        String verdictReason = verdictReason(remodeling, building);
        // 용도지역·용적률 상한(STEP1)이 실측이면 이론상 증축 상한부터 다시 잡는다 — §2.2-e "STEP 1
        // 값이 STEP 2로 흐르는 구조"(용도지역이 공공데이터와 다르게 확인되거나 지구단위계획으로 상한이
        // 달라지면 증축 가능 상한도 같이 바뀐다). STEP2가 별도로 실측되면 아래에서 그 값이 다시 이걸
        // 덮어써서 최종적으로는 더 구체적인 값(건축사 검토)이 이긴다.
        if (m.getFarLimitPct() != null) {
            remodeling = withMeasuredFarLimit(remodeling, building, m.getZoneName(), m.getFarLimitPct());
        }
        // theoreticalAdditionalBuildableAreaSqm(2026-08-27, product 요청) — STEP1 상한 기준 이론상
        // 증축 상한을 STEP2 실측 여부와 무관하게 항상 따로 내려준다. additionalBuildableAreaSqm(아래
        // areaValue)은 STEP2가 실측되면 그 값으로 바뀌어서 "이론상 상한이 뭐였는지" 비교 대상 자체가
        // 없어진다 — STEP2 "N㎡ 남김" 표시·모순 판정(검토값 > 상한) 둘 다 이 값이 있어야 가능하다.
        BigDecimal theoreticalAreaValue = remodeling.basis().additionalBuildableAreaSqm();
        // 증축면적(STEP2)이 실측이면 F-06 basis를 실측값으로 바꿔치기해서 F-08(미래가치)이 그 값을 그대로
        // 읽게 한다 — CostServiceImpl(F-07)은 additionalBuildableAreaSqm을 안 써서 이 치환의 영향을
        // 안 받는다(공사비는 STEP3 실측이 직접 대체, 아래 참고).
        boolean areaMeasured = m.getActualExpandableAreaSqm() != null;
        BigDecimal areaValue = areaMeasured ? m.getActualExpandableAreaSqm() : theoreticalAreaValue;
        if (areaMeasured) {
            remodeling = withMeasuredExpandableArea(remodeling, building, m.getActualExpandableAreaSqm());
        }
        // NOT_POSSIBLE이어도 STEP1/2 실측 기준으로 계산은 한다(product "B" 확정) — costService/
        // marketService는 verdict==NOT_POSSIBLE이면 자체적으로 공사비/미래가치를 막는다(F-05/07/08
        // 공개 화면용 게이트, 그대로 둔다). forCalc는 그 게이트만 우회하는 계산 전용 사본이고, 실제
        // verdict/verdictReason은 위에서 이미 뽑아둔 원본 값을 응답에 그대로 내려서 프론트가 경고를 띄운다.
        RemodelingResultResponse forCalc = verdict == RemodelingVerdict.NOT_POSSIBLE
                ? new RemodelingResultResponse(remodeling.score(), RemodelingVerdict.POSSIBLE, remodeling.basis())
                : remodeling;
        CostEstimationResponse cost = costService.getCostEstimation(building, forCalc);
        MarketAnalysisResponse market = marketService.getMarketAnalysis(building, forCalc);

        // 아래 4칸(증축면적·공사비·매입가·미래가치)은 각각 독립적으로 계산한다 — 하나가 안 되도 나머지를
        // 비우지 않는다(2026-08-27, product 요청 — householdCountMissing 때와 verdict 게이트 때 같은
        // 문제: 조기 반환이 계산 가능한 값까지 함께 지워서 "왜 아무것도 안 나오지"로 읽혔다). 최종 집계
        // (총투자금·ROI)만 매입가·공사비·미래가치 셋이 전부 있어야 계산되고, 셋 중 하나라도 없으면
        // null — 그래도 증축면적·공사비 개별 칸은 그대로 보여준다(§2.2-b 규칙 2).

        // 공사비(STEP3, 원 단위 입력 — F-07 minCost/maxCost와 같은 단위라 그대로 10000으로 나눠 환산).
        // hh_cnt와 무관 — grossFloorArea 기준이라 householdCountMissing의 영향을 안 받는다.
        boolean constructionMeasured = m.getActualConstructionEstimate() != null;
        BigDecimal constructionValue = constructionMeasured ? m.getActualConstructionEstimate()
                : (cost.status() == CostEstimationStatus.AVAILABLE ? cost.maxCost() : null);

        // 매입가(STEP4, 만원 단위 — F-08 currentValue와 같은 단위) — 실측 없으면 F-09와 동일 유형별 분기.
        // 세대기반 유형은 hh_cnt가 없으면(householdCountMissing) null.
        boolean purchaseMeasured = m.getActualPurchasePrice() != null;
        BigDecimal currentValue = purchaseMeasured ? m.getActualPurchasePrice()
                : (householdBased
                        ? (market.estimatedPrice().confidenceLevel() != ConfidenceLevel.UNAVAILABLE && building.getHhCnt() != null
                                ? market.estimatedPrice().value().multiply(BigDecimal.valueOf(building.getHhCnt()))
                                : null)
                        : RepresentativePriceCalculator.representativePriceOrNull(
                                market.recentTrade(), market.estimatedPrice(), building.getGfa()));

        // 미래가치(STEP5, 만원 단위 — F-08 postRemodelEstimatedPrice와 같은 단위). 세대기반 유형은
        // MarketServiceImpl.estimatePostRemodelPriceByHouseholdGrowth가 내부에서 hh_cnt를 가드한다.
        boolean futureValueMeasured = m.getPostRemodelEstimatedPrice() != null;
        BigDecimal projectedValue = futureValueMeasured ? m.getPostRemodelEstimatedPrice()
                : (market.postRemodelEstimatedPrice() != null ? market.postRemodelEstimatedPrice().value() : null);

        // 설계비·인허가비(STEP3, 원 단위) — 공공데이터에 대응 항목이 없어 실측 전용, 미입력이면 0.
        BigDecimal designPermitFeeIn10kWon = BigDecimal.ZERO;
        boolean designPermitFeeMeasured = m.getDesignFee() != null || m.getPermitFee() != null;
        if (m.getDesignFee() != null) {
            designPermitFeeIn10kWon = designPermitFeeIn10kWon.add(m.getDesignFee().divide(WON_PER_10K, 0, RoundingMode.HALF_UP));
        }
        if (m.getPermitFee() != null) {
            designPermitFeeIn10kWon = designPermitFeeIn10kWon.add(m.getPermitFee().divide(WON_PER_10K, 0, RoundingMode.HALF_UP));
        }

        // 총투자금 — 매입가·공사비만 있으면 계산된다(미래가치 불필요, 2026-08-27 product 요청 — 같은
        // 원칙의 세 번째 적용: 계산할 수 있는 것까지 묶어서 비우지 않는다).
        BigDecimal totalInvestment = null;
        if (currentValue != null && constructionValue != null) {
            BigDecimal constructionCostIn10kWon = constructionValue.divide(WON_PER_10K, 0, RoundingMode.HALF_UP);
            // 취득 부대비용 — LAW-003 §1-a 확정(2026-08-24): 취득세 중과는 "주택"만 대상이고, 주택
            // 중과는 보유 주택 수·조정대상지역 고시 원문 확인 전까지 계산에 못 쓴다(Open Item) —
            // acquisitionEntityType은 여전히 기록 전용, 기존 개인 1주택 기준 고정 요율 그대로.
            BigDecimal acquisitionCost = AcquisitionCostCalculator.calculate(currentValue, isHousing);
            totalInvestment = currentValue.add(constructionCostIn10kWon).add(designPermitFeeIn10kWon).add(acquisitionCost);
        }

        // 예상수익·ROI — 총투자금에 더해 미래가치까지 있어야 계산된다.
        BigDecimal expectedProfit = null;
        BigDecimal roi = null;
        if (totalInvestment != null && projectedValue != null) {
            expectedProfit = projectedValue.subtract(totalInvestment);
            roi = expectedProfit.multiply(BigDecimal.valueOf(100)).divide(totalInvestment, 2, RoundingMode.HALF_UP);
        }

        boolean totalInvestmentMeasured = totalInvestment != null && (purchaseMeasured || constructionMeasured || designPermitFeeMeasured);
        return new MeasurementRecalculationResponse(totalInvestment, totalInvestmentMeasured, projectedValue, futureValueMeasured,
                expectedProfit, roi,
                new ValuedField<>(areaValue, areaMeasured),
                new ValuedField<>(constructionValue, constructionMeasured),
                new ValuedField<>(currentValue, purchaseMeasured),
                isHousing, verdict, verdictReason, householdCountMissing, theoreticalAreaValue);
    }

    // verdict=NOT_POSSIBLE 사유 — RemodelingServiceImpl.evaluate()의 3개 분기(사용승인일 없음/진행중
    // 개발행위/노후연한 미달)와 정확히 대응. 프론트가 밴드 위 경고 문구로 그대로 쓴다.
    private static String verdictReason(RemodelingResultResponse remodeling, BuildingEntity building) {
        if (remodeling.verdict() != RemodelingVerdict.NOT_POSSIBLE) {
            return null;
        }
        RemodelingBasisResponse basis = remodeling.basis();
        if (basis.buildingAgeYears() == null) {
            return "사용승인일 정보가 없어 리모델링 추진 요건을 판정할 수 없습니다";
        }
        if (basis.permitInProgress()) {
            return "철거·재축·신축 등 진행 중인 개발행위가 있어 리모델링을 추진할 수 없습니다";
        }
        Integer useApprovalYear = building.getUseAprvYmd() == null ? null : building.getUseAprvYmd().getYear();
        return useApprovalYear != null
                ? "노후연한 미달 (" + useApprovalYear + "년 준공 " + basis.buildingAgeYears() + "년차 / 필요 " + basis.requiredYears() + "년)"
                : "노후연한 미달 (필요 " + basis.requiredYears() + "년)";
    }

    // 실측 용적률 상한(STEP1)으로 F-06 basis의 이론상 증축 상한을 재구성 — 여유% = 상한 − 현재 용적률
    // (currentFloorAreaRatio, 대장이 이미 계산해둔 값이라 재조회 없음), 증축가능면적 = 여유% × 대지면적
    // (landAreaSqm), RemodelingServiceImpl.evaluate()와 같은 공식(공식 이중화 방지). 현재 용적률·대지
    // 면적을 모르면(공공데이터 매칭 실패) 보정 불가 — 공공데이터 기준 그대로 둔다. zoneName도 실측값이
    // 있으면 같이 바꿔 표시가 어긋나지 않게 한다(상한은 바뀌었는데 지역명은 예전 값인 상태 방지).
    private static RemodelingResultResponse withMeasuredFarLimit(RemodelingResultResponse remodeling,
                                                                    BuildingEntity building, String measuredZoneName,
                                                                    BigDecimal measuredFarLimitPct) {
        RemodelingBasisResponse basis = remodeling.basis();
        if (basis.currentFloorAreaRatio() == null || basis.landAreaSqm() == null) {
            return remodeling;
        }
        BigDecimal correctedSurplus = measuredFarLimitPct.subtract(basis.currentFloorAreaRatio());
        BigDecimal correctedArea = correctedSurplus.max(BigDecimal.ZERO)
                .divide(BigDecimal.valueOf(100))
                .multiply(basis.landAreaSqm())
                .setScale(2, RoundingMode.HALF_UP);
        Integer correctedHouseholds = RemodelingServiceImpl.estimateAdditionalHouseholds(building, correctedArea);
        RemodelingBasisResponse correctedBasis = new RemodelingBasisResponse(
                basis.buildingAgeYears(), basis.gatePassed(), basis.gateYears(), basis.requiredYears(),
                measuredZoneName != null ? measuredZoneName : basis.zoneName(), basis.districtNames(),
                measuredFarLimitPct, correctedSurplus, basis.currentFloorAreaRatio(),
                basis.landAreaSqm(), basis.grossFloorAreaSqm(), basis.farComputationGfa(),
                correctedArea, correctedHouseholds,
                basis.recentPermitType(), basis.recentPermitDate(), basis.permitInProgress());
        return new RemodelingResultResponse(remodeling.score(), remodeling.verdict(), correctedBasis);
    }

    // 실측 증축면적으로 F-06 basis를 재구성 — estimatedAdditionalHouseholds(세대기반 유형용)도 그 면적
    // 기준으로 다시 계산한다(RemodelingServiceImpl과 같은 공식 재사용, 공식 이중화 방지).
    private static RemodelingResultResponse withMeasuredExpandableArea(RemodelingResultResponse remodeling,
                                                                          BuildingEntity building, BigDecimal measuredArea) {
        RemodelingBasisResponse basis = remodeling.basis();
        Integer measuredHouseholds = RemodelingServiceImpl.estimateAdditionalHouseholds(building, measuredArea);
        RemodelingBasisResponse measuredBasis = new RemodelingBasisResponse(
                basis.buildingAgeYears(), basis.gatePassed(), basis.gateYears(), basis.requiredYears(),
                basis.zoneName(), basis.districtNames(), basis.floorAreaRatioLimit(), basis.floorAreaRatioSurplus(),
                basis.currentFloorAreaRatio(), basis.landAreaSqm(), basis.grossFloorAreaSqm(), basis.farComputationGfa(),
                measuredArea, measuredHouseholds,
                basis.recentPermitType(), basis.recentPermitDate(), basis.permitInProgress());
        return new RemodelingResultResponse(remodeling.score(), remodeling.verdict(), measuredBasis);
    }

    // ── 항목 상태(추정/실측/재확인) — 별도 플래그 없이 매번 시각 비교로 판정(§2.2-b). anchorDate/
    // elapsedDays도 여기서 같이 계산해 내려준다(§2.3-c "경과일·재확인 판정은 프론트에서 계산하지 않는다").
    private List<MeasurementItemStatusResponse> computeItemStatuses(MeasurementEntity m, Map<String, ValidityRule> validity) {
        List<MeasurementItemStatusResponse> result = new ArrayList<>();
        for (MeasurementItem item : MeasurementItem.values()) {
            LocalDateTime inputAt = inputAtOf(m, item);
            if (inputAt == null) {
                result.add(new MeasurementItemStatusResponse(item.name(), item.stepNo(), MeasurementItemStatus.ESTIMATED, null, null, null, null));
                continue;
            }
            ValidityRule rule = validity.get(item.name());
            String anchorUsed = resolveAnchorUsed(m, item, rule);
            LocalDate anchorDate = ValidityAnchor.DOCUMENT_DATE.name().equals(anchorUsed) ? documentDateOf(m, item) : inputAt.toLocalDate();
            long elapsedDays = ChronoUnit.DAYS.between(anchorDate, LocalDate.now());
            boolean stale = isDependencyStale(m, item, inputAt) || isExpired(m, item, inputAt, rule, anchorUsed);
            result.add(new MeasurementItemStatusResponse(item.name(), item.stepNo(),
                    stale ? MeasurementItemStatus.RECHECK : MeasurementItemStatus.MEASURED, inputAt, anchorUsed,
                    anchorDate, elapsedDays));
        }
        return result;
    }

    // §2.2 의존 순서: STEP1(규제 확인 3항목) → STEP2(증축면적) → {STEP3 견적, STEP5 미래가치}.
    // STEP4(매입 조건)는 앞 단계에 의존하지 않는다.
    private boolean isDependencyStale(MeasurementEntity m, MeasurementItem item, LocalDateTime inputAt) {
        if (item == MeasurementItem.EXPANDABLE_AREA) {
            return STEP1_ITEMS.stream().map(i -> inputAtOf(m, i)).filter(Objects::nonNull).anyMatch(t -> t.isAfter(inputAt));
        }
        if (item == MeasurementItem.ESTIMATE || item == MeasurementItem.FUTURE_VALUE) {
            LocalDateTime areaInputAt = m.getExpandableAreaInputAt();
            return areaInputAt != null && areaInputAt.isAfter(inputAt);
        }
        return false;
    }

    // §3.1-a(2026-08-24) — DOCUMENT_DATE 항목(안전진단·실제 견적·증축 가능 연면적)이라도 서류 날짜를
    // 안 넣었으면 입력 시각으로 폴백한다("서류 날짜는 선택 입력"). 실제로 어느 쪽을 썼는지를 응답에
    // 그대로 노출해 프론트가 추측하지 않게 한다.
    private String resolveAnchorUsed(MeasurementEntity m, MeasurementItem item, ValidityRule rule) {
        if (rule != null && rule.anchor() == ValidityAnchor.DOCUMENT_DATE && documentDateOf(m, item) != null) {
            return ValidityAnchor.DOCUMENT_DATE.name();
        }
        return ValidityAnchor.INPUT_AT.name();
    }

    private LocalDate documentDateOf(MeasurementEntity m, MeasurementItem item) {
        return switch (item) {
            case SAFETY -> {
                SafetyInspectionResponse safety = readSafety(m.getSafetyInspection());
                yield safety != null ? safety.inspectionDate() : null;
            }
            case ESTIMATE -> m.getEstimateDocumentDate();
            case EXPANDABLE_AREA -> m.getExpandableAreaDocumentDate();
            default -> null;
        };
    }

    private boolean isExpired(MeasurementEntity m, MeasurementItem item, LocalDateTime inputAt, ValidityRule rule, String anchorUsed) {
        if (rule == null || rule.validDays() == null) {
            return false;
        }
        LocalDateTime anchor = ValidityAnchor.DOCUMENT_DATE.name().equals(anchorUsed)
                ? documentDateOf(m, item).atStartOfDay()
                : inputAt;
        return anchor.plusDays(rule.validDays()).isBefore(LocalDateTime.now());
    }

    private LocalDateTime inputAtOf(MeasurementEntity m, MeasurementItem item) {
        return switch (item) {
            case ZONING -> m.getZoningInputAt();
            case HEIGHT_LIMIT -> m.getHeightLimitInputAt();
            case SAFETY -> m.getSafetyInputAt();
            case EXPANDABLE_AREA -> m.getExpandableAreaInputAt();
            case REDUCTION_REASON -> m.getReductionReasonInputAt();
            case ESTIMATE -> m.getEstimateInputAt();
            case ESTIMATE_BASIS -> m.getEstimateBasisInputAt();
            case DESIGN_PERMIT_FEE -> m.getDesignPermitFeeInputAt();
            case PURCHASE_PRICE -> m.getPurchasePriceInputAt();
            case ACQUISITION_ENTITY -> m.getAcquisitionEntityInputAt();
            case REGISTRY_RIGHTS -> m.getRegistryRightsInputAt();
            case LEASE_VACANCY -> m.getLeaseVacancyInputAt();
            case FUTURE_VALUE -> m.getFutureValueInputAt();
            case FUTURE_VALUE_BASIS -> m.getFutureValueBasisInputAt();
        };
    }

    private MeasurementProgressResponse coreProgress(MeasurementEntity m) {
        long measured = CORE_ITEMS.stream().filter(i -> inputAtOf(m, i) != null).count();
        return new MeasurementProgressResponse((int) measured, CORE_ITEMS.size());
    }

    private String nextInputField(MeasurementEntity m) {
        return CORE_ITEMS.stream().filter(i -> inputAtOf(m, i) == null).map(Enum::name).findFirst().orElse(null);
    }

    private record ValidityRule(Integer validDays, ValidityAnchor anchor) {
    }

    // Collectors.toMap은 값이 null이면 내부적으로 NPE를 던진다 — validDays가 null인 행("안 낡음" 항목,
    // 취득주체·기록 항목들)이 실제로 있어 HashMap에 직접 채운다.
    private Map<String, ValidityRule> loadValidity() {
        Map<String, ValidityRule> map = new HashMap<>();
        measurementValidityRepository.findAll().forEach(v -> map.put(v.getItemKey(), new ValidityRule(v.getValidDays(), v.getAnchor())));
        return map;
    }

    private SafetyInspectionResponse readSafety(String json) {
        return json == null ? null : objectMapper.readValue(json, SafetyInspectionResponse.class);
    }

    private MeasurementDetailResponse.Values toValues(MeasurementEntity m) {
        return new MeasurementDetailResponse.Values(
                m.getZoneName(), m.getFarLimitPct(), m.getHeightLimit(), m.getDistrictPlan(),
                readSafety(m.getSafetyInspection()),
                m.getActualExpandableAreaSqm(), m.getExpandableAreaDocumentDate(), m.getReductionReason(),
                m.getActualConstructionEstimate(), m.getEstimateDocumentDate(),
                m.getEstimateSiteCondition(), m.getEstimateSiteNote(), m.getEstimateSource(),
                m.getDesignFee(), m.getPermitFee(),
                m.getActualPurchasePrice(), m.getAcquisitionEntityType(),
                m.getRegistryRightsStatus(), m.getLeaseVacancyCondition(),
                m.getPostRemodelEstimatedPrice(), m.getValuationBasisMemo());
    }

    private String writeJson(Object value) {
        return value == null ? null : objectMapper.writeValueAsString(value);
    }

    private Long resolveUserId(String email) {
        return userRepository.findByEmail(email)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new InvalidCredentialsException("존재하지 않는 계정입니다."))
                .getId();
    }
}
