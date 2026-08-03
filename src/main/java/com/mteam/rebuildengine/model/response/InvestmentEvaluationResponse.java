package com.mteam.rebuildengine.model.response;

import com.mteam.rebuildengine.utils.InvestmentGrade;

import java.math.BigDecimal;

// FEATURE_09_INVESTMENT.md §3.2 GET /api/v1/properties/{buildingId}/investment 응답. roi는
// stage가 FULL일 때만 값이 있고, GATE/SCORE_FALLBACK이면 null(ROI 산출 자체를 하지 않았다는 뜻 —
// "산출은 됐는데 낮다"와 구분, DOMAIN.md §7.2 "없음과 낮음은 다르다").
public record InvestmentEvaluationResponse(InvestmentGrade grade, BigDecimal roi, InvestmentEvaluationStage stage) {
}
