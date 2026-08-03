package com.mteam.rebuildengine.model.response;

// FEATURE_09_INVESTMENT.md §3.2 3단계 판정 구조. GATE=F-06 "불가"(D 고정, roi 없음),
// SCORE_FALLBACK=4대 입력값 중 하나라도 없음(F-06 score 단독 폴백, roi 없음),
// FULL=4대 입력값 전부 있음(F-08 §3.7 예상수익률 기반 정식 산출, roi 있음).
public enum InvestmentEvaluationStage {
    GATE,
    SCORE_FALLBACK,
    FULL
}
