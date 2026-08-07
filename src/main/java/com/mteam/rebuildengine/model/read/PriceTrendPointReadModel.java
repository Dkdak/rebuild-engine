package com.mteam.rebuildengine.model.read;

import java.math.BigDecimal;

// F-08 §3.8 "시세 추이" — 월 단위로 집계한 유사거래 ㎡당 가격 중앙값(month는 "YYYY-MM"). 표본이
// 3건 미만인 달은 애초에 이 목록에 없다(MarketMapper.xml의 HAVING, TradeStatsIndex.monthlyTrend()
// 양쪽에서 동일 기준). 라이브 DB 조회(MarketMapper)·배치 메모리 조회(TradeStatsIndex) 양쪽이 같은
// 모양으로 반환해 MarketServiceImpl이 조회 경로와 무관하게 하나의 변환 로직만 쓴다.
public record PriceTrendPointReadModel(String month, BigDecimal medianPricePerSqm, long tradeCount) {
}
