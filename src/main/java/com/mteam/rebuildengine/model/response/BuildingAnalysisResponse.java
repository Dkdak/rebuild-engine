package com.mteam.rebuildengine.model.response;

import com.mteam.rebuildengine.utils.InvestmentGrade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// FEATURE_09_INVESTMENT.md §3.4 GET /api/v1/properties/{buildingId}/analysis 응답 — investment_result
// 한 행을 그대로 읽어 F-06/F-07/F-08 원본 응답 모양 그대로 반환한다(실시간 재계산 아님). F-05/F-10이
// .../remodeling·.../cost·.../market 3개 호출 대신 이 API 하나만 부른다. updatedAt은 화면의
// "최근 갱신: YYYY-MM-DD" 라벨용 — 실시간 값이 아니라는 걸 사용자에게 안내하기 위해 필요.
public record BuildingAnalysisResponse(
        InvestmentGrade grade,
        BigDecimal roi,
        RemodelingResultResponse remodeling,
        CostEstimationResponse cost,
        MarketAnalysisResponse market,
        LocalDateTime updatedAt
) {
}
