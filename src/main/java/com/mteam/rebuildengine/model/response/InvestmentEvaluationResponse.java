package com.mteam.rebuildengine.model.response;

import com.mteam.rebuildengine.utils.InvestmentEvaluationStage;
import com.mteam.rebuildengine.utils.InvestmentGrade;

import java.math.BigDecimal;

// FEATURE_09_INVESTMENT.md §3.2/§3.3 GET /api/v1/properties/{buildingId}/investment 응답. grade는
// stage와 무관하게 항상 값이 있다 — GATE는 grade=D(확정 판정), SCORE_FALLBACK은 grade=NA(정보부족,
// 2026-08-09 변경). 이전엔 F-06 achievementRate를 grade로 대체했으나, 그 값이 건물 나이에 비례해
// 무한정 커져 대지면적·시세 등 핵심 정보가 없는 건물일수록 오히려 고등급(A+)으로 몰리는 문제가
// 확인돼 A~D 산출 자체를 포기하고 NA로 명확히 구분. roi는 stage가 FULL일 때만 값이 있고, GATE/
// SCORE_FALLBACK이면 null — "산출은 됐는데 낮다"와 "산출 자체를 안 했다"는 구분
// (DOMAIN.md §7.2 "없음과 낮음은 다르다").
public record InvestmentEvaluationResponse(InvestmentGrade grade, BigDecimal roi, InvestmentEvaluationStage stage) {
}
