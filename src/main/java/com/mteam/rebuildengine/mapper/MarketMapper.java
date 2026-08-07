package com.mteam.rebuildengine.mapper;

import com.mteam.rebuildengine.model.read.ComparableTradeSampleReadModel;
import com.mteam.rebuildengine.model.read.ComparableTradeStatsReadModel;
import com.mteam.rebuildengine.model.read.PriceTrendPointReadModel;
import com.mteam.rebuildengine.model.read.TradeStatRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface MarketMapper {
    // F-08 §3.4-B/§3.5 — 조건에 맞는 유사 거래의 ㎡당 가격 중앙값·건수를 한 번에 집계.
    ComparableTradeStatsReadModel findComparableTradeStats(MarketComparableCondition condition);

    // F-10 "유사 사례"(§2.9) 근거용 — 위와 완전히 같은 조건으로 개별 거래 상위 5건(계약일 최신순)만
    // 뽑는다. comparableCount > 0일 때만(완화 단계가 성공한 경우만) 호출.
    List<ComparableTradeSampleReadModel> findComparableTradeSamples(MarketComparableCondition condition);

    // F-09 V1 배치 전용(TradeStatsIndex) — recency 범위 내 유사거래 비교 후보 전체를 1회만 읽어온다.
    // 건물별로 매번 다시 조회하지 않고 배치 시작 시 딱 한 번 호출.
    List<TradeStatRow> findAllComparableTradeRows(@Param("recencyCutoff") LocalDate recencyCutoff);

    // F-08 §3.8 "시세 추이" — findComparableTradeStats와 완전히 같은 조건(§3.4-B/§3.5 0/1단계, 2단계
    // 범위 확대는 쓰지 않음)으로 최근 36개월을 계약월별로 묶어 ㎡당 가격 중앙값을 낸다. 표본 3건 미만인
    // 달은 결과에서 아예 빠진다(HAVING).
    List<PriceTrendPointReadModel> findMonthlyPriceTrend(MarketComparableCondition condition);
}
