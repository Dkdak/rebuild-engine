package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;

// FEATURE.md §8.17 "시장 내 가격 위치" — estimatedPrice가 실제로 resolve된 단계(matchStage)와 완전히
// 같은 비교거래 모집단 안에서 ㎡당가 분포. p25/median/p75는 만원 단위 ㎡당가, thisPropertyPercentile은
// 이 매물(§8.16 지분거래 판정 적용된 "대표 가격")이 그 분포 안에서 몇 percentile인지(0~100, 값이 클수록
// 비싼 쪽 — "상위 N%"로 보여주려면 프론트에서 100-thisPropertyPercentile로 환산). estimatedPrice
// 자체가 UNAVAILABLE이면(비교거래 자체가 없음) 이 필드도 null.
//
// p25Total/medianTotal/p75Total(2026-08-27 추가, F-19 product 요청) — 위 셋을 "이 매물 전체" 총액
// (만원)으로 환산한 값. 세대기반 유형(아파트·연립다세대)은 ㎡당가 × 세대당면적 × 세대수, 그 외는
// ㎡당가 × 건물 전체 면적 — F-19 매입가(currentValue)가 항상 "건물 전체 총액" 단위인 것과 맞춘다.
// 프론트가 직접 곱하면 세대기반 유형에서 서버 기준과 어긋나므로(예: 세대수 누락) 서버가 미리 계산해서
// 내려준다 — 사용자가 타이핑하는 매입가(총액)를 이 값과 그대로 비교할 수 있게 하기 위함. areaBase
// (targetArea 또는 targetArea×hhCnt)를 모르면(유형 분류 실패 등) 셋 다 null.
//
// estimateFallback(2026-08-27 추가, DOMAIN.md §7.5 "폴백값을 실제 값처럼 표시하지 않는다") — 이 매물의
// ㎡당가를 실측/대표 실거래로 못 구해서 모집단 중앙값으로 대신 채운 경우 true. 그 경우
// thisPropertyPercentile은 정의상 항상 50 근처로 나오는데, "이 매물이 딱 중간"이 아니라 "위치를
// 몰라서 중간에 둔 것"이므로 프론트는 마커를 숨기거나 "추정 불가"로 표시해야 한다. Boolean(래퍼) —
// investment_result에 저장된 배치 스냅샷(이 필드 추가 이전 시점)을 역직렬화할 때 이 키가 없으면
// null이 되는데, primitive boolean이면 그 순간 MismatchedInputException으로 /analysis 전체가 500이
// 된다(2026-08-27 발견·수정). null은 "그 시점 스냅샷엔 이 판정이 없었다"로 해석 — false와 다르다.
public record PricePositionResponse(BigDecimal p25, BigDecimal median, BigDecimal p75, BigDecimal thisPropertyPercentile,
                                     BigDecimal p25Total, BigDecimal medianTotal, BigDecimal p75Total,
                                     Boolean estimateFallback) {
}
