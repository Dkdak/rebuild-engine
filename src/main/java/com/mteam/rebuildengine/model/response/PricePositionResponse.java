package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;

// FEATURE.md §8.17 "시장 내 가격 위치" — estimatedPrice가 실제로 resolve된 단계(matchStage)와 완전히
// 같은 비교거래 모집단 안에서 ㎡당가 분포. p25/median/p75는 만원 단위 ㎡당가, thisPropertyPercentile은
// 이 매물(§8.16 지분거래 판정 적용된 "대표 가격")이 그 분포 안에서 몇 percentile인지(0~100, 값이 클수록
// 비싼 쪽 — "상위 N%"로 보여주려면 프론트에서 100-thisPropertyPercentile로 환산). estimatedPrice
// 자체가 UNAVAILABLE이면(비교거래 자체가 없음) 이 필드도 null.
public record PricePositionResponse(BigDecimal p25, BigDecimal median, BigDecimal p75, BigDecimal thisPropertyPercentile) {
}
