package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.model.response.MarketAnalysisResponse;
import com.mteam.rebuildengine.model.response.RemodelingResultResponse;

import java.util.Optional;

public interface MarketService {
    Optional<MarketAnalysisResponse> getMarketAnalysis(String buildingId);

    // InvestmentService(F-09)가 이미 조회한 BuildingEntity·F-06 결과를 그대로 넘겨 중복 조회/재계산을
    // 피한다(배치에서 건물당 F-06을 두 번 계산하던 문제, 2026-08-08 발견).
    MarketAnalysisResponse getMarketAnalysis(BuildingEntity building, RemodelingResultResponse remodeling);

    // F-09 V1 배치(InvestmentAnalysisBatchService) 전용 — 페이지 단위 벌크 조회 결과(BuildingDataBundle)와
    // 배치 시작 시 1회 로드한 유사거래 인덱스(TradeStatsIndex)를 그대로 사용해 건물별 DB 재조회를
    // 완전히 피한다(F-08 §3.4-B 유사거래 검색까지 포함).
    MarketAnalysisResponse getMarketAnalysis(BuildingEntity building, RemodelingResultResponse remodeling,
                                              BuildingDataBundle bundle, TradeStatsIndex tradeStatsIndex);

    // F-09 V1 배치 전용 — 배치 시작 시 딱 1번만 호출. §3.4-B-3 recency 기준(RECENCY_WINDOW_MONTHS)을
    // 여기서 단일 소스로 관리해 배치·라이브 조회가 서로 다른 값을 쓰지 않게 한다.
    TradeStatsIndex loadTradeStatsIndex();
}
