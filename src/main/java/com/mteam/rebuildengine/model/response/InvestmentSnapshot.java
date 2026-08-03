package com.mteam.rebuildengine.model.response;

// FEATURE_09_INVESTMENT.md §3.4 — F-06/F-07/F-08 원본 응답 + F-09 등급 산정 결과를 한 번에 묶은 값.
// InvestmentAnalysisBatchService가 이 4개를 JSON 직렬화해 investment_result 한 행에 저장한다
// (remodeling→remodeling_basis, cost→cost_basis, market→market_basis, investment.grade/roi→flat 컬럼).
public record InvestmentSnapshot(
        RemodelingResultResponse remodeling,
        CostEstimationResponse cost,
        MarketAnalysisResponse market,
        InvestmentEvaluationResponse investment
) {
}
