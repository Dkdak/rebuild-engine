package com.mteam.rebuildengine.model.response;

// F-08 §3.5 단계적 완화의 신뢰도 라벨. 3단계(공시가격 기반 근사)는 검증된 "실거래가 대비 공시가격
// 비율"이 없어 구현하지 않는다(§3.5 "근거 없는 비율을 임의로 가정하지 않는다") — 그래서 값 자체가 없다.
// DONG_TYPE_AVERAGE/GU_TYPE_AVERAGE(2026-08-08 추가) — WIDENED_RANGE까지 실패했을 때, 면적·연식
// 조건 없이 "그 법정동/구의 같은 유형 실거래가 전체" 평균으로 한 번 더 완화하는 단계. 여전히 실제
// 관측된 거래에서만 뽑는다(임의 비율 아님), 다만 대상 매물과의 면적·연식 유사성은 더 이상 안 봄 —
// 그만큼 신뢰도가 WIDENED_RANGE보다도 낮다(프론트 라벨은 "참고용" 수준으로 표시 권장).
public enum ConfidenceLevel {
    SAME_DONG,
    SAME_GU,
    WIDENED_RANGE,
    DONG_TYPE_AVERAGE,
    GU_TYPE_AVERAGE,
    UNAVAILABLE
}
