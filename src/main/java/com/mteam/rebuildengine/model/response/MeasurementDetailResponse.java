package com.mteam.rebuildengine.model.response;

import com.mteam.rebuildengine.model.entity.AcquisitionEntityType;
import com.mteam.rebuildengine.model.entity.SiteCondition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// F-19 GET /api/v1/analysis/measurements/{buildingId}(§3.2) — 저장된 실측값 전체 + 항목별 상태 +
// 마지막 재계산 결과 + 진행도. values는 measurement 엔티티 필드를 그대로 노출한다(HELP2 §6.1 —
// Entity 직접 노출 금지 원칙에 따라 별도 DTO).
public record MeasurementDetailResponse(
        String buildingId,
        Values values,
        List<MeasurementItemStatusResponse> itemStatuses,
        MeasurementRecalculationResponse recalculation,
        MeasurementProgressResponse progress
) {
    public record Values(
            String zoneName, BigDecimal farLimitPct,
            String heightLimit, String districtPlan,
            SafetyInspectionResponse safetyInspection,
            BigDecimal actualExpandableAreaSqm, LocalDate expandableAreaDocumentDate, String reductionReason,
            BigDecimal actualConstructionEstimate, LocalDate estimateDocumentDate,
            SiteCondition estimateSiteCondition, String estimateSiteNote, String estimateSource,
            BigDecimal designFee, BigDecimal permitFee,
            BigDecimal actualPurchasePrice, AcquisitionEntityType acquisitionEntityType,
            String registryRightsStatus, String leaseVacancyCondition,
            BigDecimal postRemodelEstimatedPrice, String valuationBasisMemo
    ) {
    }
}
