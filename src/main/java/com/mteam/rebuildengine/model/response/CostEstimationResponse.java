package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;

// F-07 §3.4 GET /api/v1/properties/{buildingId}/cost 응답. minCost/maxCost는 status가
// AVAILABLE이 아니면 null.
public record CostEstimationResponse(BigDecimal minCost, BigDecimal maxCost, CostEstimationStatus status,
                                      CostBasisResponse basis) {

    public static CostEstimationResponse notApplicable(CostEstimationStatus status) {
        return new CostEstimationResponse(null, null, status, null);
    }
}
