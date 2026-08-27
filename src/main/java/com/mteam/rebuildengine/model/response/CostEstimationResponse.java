package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;

// F-07 §3.4 GET /api/v1/properties/{buildingId}/cost 응답. minCost/maxCost/defaultCost는 status가
// AVAILABLE이 아니면 null. defaultCost(2026-08-24 추가, FEATURE_19_PERSONALIZED_ANALYSIS.md §2.2-c
// STEP3 참고표 "중간" 행 — basis.agingFactorDefault로 계산한 값, min/max와 같은 방식(§2.4 "기준"
// 시나리오와 동일 배율 변환)이라 소비 측이 직접 재계산할 필요가 없다.
public record CostEstimationResponse(BigDecimal minCost, BigDecimal defaultCost, BigDecimal maxCost,
                                      CostEstimationStatus status, CostBasisResponse basis) {

    public static CostEstimationResponse notApplicable(CostEstimationStatus status) {
        return new CostEstimationResponse(null, null, null, status, null);
    }
}
