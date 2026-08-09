package com.mteam.rebuildengine.model.read;

// §8.17 "거래 활성도" — MarketMapper.findTradeActivityCounts(라이브)/TradeStatsIndex.ActivityCounts
// (배치)가 같은 모양으로 반환. 둘 다 TradeActivityResponse로 그대로 변환된다.
public record TradeActivityReadModel(long recent1yCount, long recent3yCount, long recent5yCount) {
}
