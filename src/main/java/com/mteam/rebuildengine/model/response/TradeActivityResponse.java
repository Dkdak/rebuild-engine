package com.mteam.rebuildengine.model.response;

// FEATURE.md §8.17 "거래 활성도" — 법정동×유형 비교거래 모집단(estimatedPrice §3.4-B SAME_DONG
// 단계와 완전히 같은 필터, 면적±20%·연식±5년) 안에서 최근 1/3/5년 거래건수. 유형 분류 자체가
// 안 되거나(오피스텔 등) 대상 면적을 못 구하면 이 필드 자체가 null — "비교 모집단을 정의할 수
// 없음"과 "모집단은 있는데 최근 거래가 0건"은 다르다(0건도 유효한 값, DOMAIN.md §7.2).
public record TradeActivityResponse(int recent1yCount, int recent3yCount, int recent5yCount) {
}
