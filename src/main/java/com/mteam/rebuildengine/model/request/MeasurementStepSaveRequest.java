package com.mteam.rebuildengine.model.request;

import com.mteam.rebuildengine.model.entity.AcquisitionEntityType;
import com.mteam.rebuildengine.model.entity.SiteCondition;

import java.math.BigDecimal;
import java.time.LocalDate;

// F-19 PUT /api/v1/analysis/measurements/{buildingId}/steps/{stepNo}(FEATURE_19_PERSONALIZED_ANALYSIS.md
// §3.2) — 필드 전체를 한 레코드에 담되, 서비스 레이어가 stepNo에 해당하는 필드만 읽고 나머지는 무시한다.
// "저장 단위는 단계다"(§2.2-b) — 화면이 그 단계 항목만 보내면 된다.
public record MeasurementStepSaveRequest(
        // STEP 1
        String zoneName, BigDecimal farLimitPct,
        String heightLimit, String districtPlan,
        String safetyGrade, LocalDate safetyInspectionDate, String safetyAllowedExpansionType,
        // STEP 2
        BigDecimal actualExpandableAreaSqm, LocalDate expandableAreaDocumentDate, String reductionReason,
        // STEP 3
        BigDecimal actualConstructionEstimate, LocalDate estimateDocumentDate,
        SiteCondition estimateSiteCondition, String estimateSiteNote, String estimateSource,
        BigDecimal designFee, BigDecimal permitFee,
        // STEP 4
        BigDecimal actualPurchasePrice, AcquisitionEntityType acquisitionEntityType,
        String registryRightsStatus, String leaseVacancyCondition,
        // STEP 5
        BigDecimal postRemodelEstimatedPrice, String valuationBasisMemo
) {
}
