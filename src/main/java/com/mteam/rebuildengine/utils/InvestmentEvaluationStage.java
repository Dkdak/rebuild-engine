package com.mteam.rebuildengine.utils;

// FEATURE_09_INVESTMENT.md §3.2 3단계 판정 구조. GATE=F-06 "불가"(D 고정, roi 없음),
// SCORE_FALLBACK=4대 입력값 중 하나라도 없음(grade/roi 둘 다 없음, 2026-08-09 변경 — F-06 achievementRate는
// 나이에 비례해 무한정 커져 실제 투자가치와 무관하게 등급을 인플레이션시킨다는 게 밝혀져, 이 값을 grade로
// 대체하지 않기로 기획 확정. §3.2 "정보 부족" 참고), FULL=4대 입력값 전부 있음(F-08 §3.7 예상수익률
// 기반 정식 산출, grade/roi 둘 다 있음). InvestmentGrade와 같은 utils 패키지 — entity/response 양쪽에서
// 참조해야 해서(InvestmentResultEntity.stage) response 패키지에 두지 않는다.
public enum InvestmentEvaluationStage {
    GATE,
    SCORE_FALLBACK,
    FULL
}
