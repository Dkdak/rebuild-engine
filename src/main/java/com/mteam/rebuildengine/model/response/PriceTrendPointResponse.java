package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;

// F-08 §3.8 "시세 추이" — month는 "YYYY-MM" 표기, 결측월은 이 목록에 아예 없음(연속 배열 아님).
public record PriceTrendPointResponse(String month, BigDecimal medianPricePerSqm, int tradeCount) {
}
