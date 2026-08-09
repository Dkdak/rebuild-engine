package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;

// F-08 §3.6 GET /api/v1/properties/{buildingId}/market 응답. recentTrade는 §3.4-A 매칭 성공한
// 아파트/연립다세대/오피스텔만 값 있음(그 외 유형은 항상 null, §3.3). officialPrice/landPrice는
// building_id 매칭이 안 됐으면 null.
// postRemodelEstimatedPrice(§3.7) — F-06 "불가" 판정이거나 additionalBuildableAreaSqm이 없으면,
// 또는 계산은 됐지만 confidenceLevel이 UNAVAILABLE이면 null(estimatedPrice와 달리 UNAVAILABLE
// 객체를 내려보내지 않고 필드 자체를 없앤다 — 절반만 계산된 값을 노출하지 않기 위함).
// priceTrend(§3.8, 2026-08-08 추가) — estimatedPrice와 같은 유형/면적 기준(현재 상태)의 월별 추이.
// 법정동→구 단계까지만 시도하고(범위 확대 단계는 안 씀), 둘 다 표본이 부족하면 null.
// tradeActivity/pricePosition(§8.17 F-10 "04 시장 분석" 신규 카드, 2026-08-09 추가) — 유형 분류
// 자체가 안 되거나 대상 면적을 못 구하면(estimatedPrice가 UNAVAILABLE이 되는 것과 같은 조건) 둘 다
// null. pricePosition은 estimatedPrice가 UNAVAILABLE이면 비교할 분포 자체가 없어 마찬가지로 null.
public record MarketAnalysisResponse(
        RecentTradeResponse recentTrade,
        EstimatedPriceResponse estimatedPrice,
        BigDecimal officialPrice,
        BigDecimal landPrice,
        EstimatedPriceResponse postRemodelEstimatedPrice,
        PriceTrendResponse priceTrend,
        TradeActivityResponse tradeActivity,
        PricePositionResponse pricePosition
) {
}
