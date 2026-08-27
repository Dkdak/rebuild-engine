package com.mteam.rebuildengine.service.analysis;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.model.response.AgeAdjustedPriceResponse;
import com.mteam.rebuildengine.model.response.MarketAnalysisResponse;
import com.mteam.rebuildengine.model.response.PricePositionResponse;
import com.mteam.rebuildengine.model.response.RemodelingResultResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface MarketService {
    Optional<MarketAnalysisResponse> getMarketAnalysis(String buildingId);

    // FEATURE_10_MARKET.md CASE2 — "시장 내 가격 위치"를 실제 매입가(F-19 실측, 만원 단위 총액) 기준으로
    // 재계산한다. 모집단(p25/median/p75)은 공공데이터 기준 estimatedPrice와 완전히 같은 캐스케이드로
    // 정하고, thisPropertyPercentile만 recentTrade 대신 이 값으로 순위를 다시 매긴다. 유형 분류 실패·
    // 대상 면적 산출 불가·비교거래 자체가 없으면 빈 값(호출부가 공공데이터 기준으로 폴백).
    Optional<PricePositionResponse> getMeasuredPricePosition(BuildingEntity building, BigDecimal measuredPurchasePrice);

    // FEATURE_19_PERSONALIZED_ANALYSIS.md §2.2-a "유효연식 참고표" 선행 작업(FEATURE_08_MARKET.md §3.7
    // 확장, 2026-08-24) — 보정 없음/−10/−15/−20년 4행 고정. F-06 "불가" 판정이거나 유형 분류 실패면
    // 빈 리스트(postRemodelEstimatedPrice가 null이 되는 것과 같은 기준).
    List<AgeAdjustedPriceResponse> getPostRemodelPriceByAge(String buildingId);

    // InvestmentService(F-09)가 이미 조회한 BuildingEntity·F-06 결과를 그대로 넘겨 중복 조회/재계산을
    // 피한다(배치에서 건물당 F-06을 두 번 계산하던 문제, 2026-08-08 발견).
    MarketAnalysisResponse getMarketAnalysis(BuildingEntity building, RemodelingResultResponse remodeling);

    // F-09 V1 배치(InvestmentAnalysisBatchService) 전용 — 페이지 단위 벌크 조회 결과(BuildingDataBundle)와
    // 배치 시작 시 1회 로드한 유사거래 인덱스(TradeStatsIndex)를 그대로 사용해 건물별 DB 재조회를
    // 완전히 피한다(F-08 §3.4-B 유사거래 검색까지 포함). tradeActivityIndex(§8.17, 2026-08-09 추가)는
    // "거래 활성도" 전용 — tradeStatsIndex(가격 통계, 36개월)와 별개로 60개월 창을 쓴다.
    MarketAnalysisResponse getMarketAnalysis(BuildingEntity building, RemodelingResultResponse remodeling,
                                              BuildingDataBundle bundle, TradeStatsIndex tradeStatsIndex,
                                              TradeStatsIndex tradeActivityIndex);

    // F-09 V1 배치 전용 — 배치 시작 시 딱 1번만 호출. §3.4-B-3 recency 기준(RECENCY_WINDOW_MONTHS)을
    // 여기서 단일 소스로 관리해 배치·라이브 조회가 서로 다른 값을 쓰지 않게 한다.
    TradeStatsIndex loadTradeStatsIndex();

    // §8.17 "거래 활성도" 전용 인덱스 로딩 — TRADE_ACTIVITY_WINDOW_MONTHS(60개월)를 여기서 단일 관리.
    TradeStatsIndex loadTradeActivityIndex();
}
