package com.mteam.rebuildengine.mapper;

import com.mteam.rebuildengine.model.read.ComparableTradeStatsReadModel;
import com.mteam.rebuildengine.model.read.TradeStatRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface MarketMapper {
    // F-08 §3.4-B/§3.5 — 조건에 맞는 유사 거래의 ㎡당 가격 중앙값·건수를 한 번에 집계.
    ComparableTradeStatsReadModel findComparableTradeStats(MarketComparableCondition condition);

    // F-09 V1 배치 전용(TradeStatsIndex) — recency 범위 내 유사거래 비교 후보 전체를 1회만 읽어온다.
    // 건물별로 매번 다시 조회하지 않고 배치 시작 시 딱 한 번 호출.
    List<TradeStatRow> findAllComparableTradeRows(@Param("recencyCutoff") LocalDate recencyCutoff);
}
