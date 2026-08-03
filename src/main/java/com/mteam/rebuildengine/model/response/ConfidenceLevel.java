package com.mteam.rebuildengine.model.response;

// F-08 §3.5 단계적 완화의 신뢰도 라벨. 3단계(공시가격 기반 근사)는 검증된 "실거래가 대비 공시가격
// 비율"이 없어 구현하지 않는다(§3.5 "근거 없는 비율을 임의로 가정하지 않는다") — 그래서 값 자체가 없다.
public enum ConfidenceLevel {
    SAME_DONG,
    SAME_GU,
    WIDENED_RANGE,
    UNAVAILABLE
}
