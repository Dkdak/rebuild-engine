package com.mteam.rebuildengine.model.read;

import java.util.List;

// F-08 §3.4-B 유사거래 비교 한 단계(법정동/구/범위확대)의 결과 — 집계 통계(stats)와 그 계산에
// 실제로 쓰인 개별 거래 상위 5건(samples, F-10 §2.9 근거용)을 함께 묶는다. DB 조회(라이브 단건)든
// TradeStatsIndex 인메모리 조회(배치)든 이 타입 하나로 통일해 MarketServiceImpl의 3단계 완화
// 로직(estimatePriceForArea)이 조회 방식과 무관하게 동일하게 동작하게 한다.
public record ComparableTradeSearchResult(ComparableTradeStatsReadModel stats, List<ComparableTradeSampleReadModel> samples) {
}
