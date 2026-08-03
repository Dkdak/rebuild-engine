package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.model.response.BuildingAnalysisResponse;
import com.mteam.rebuildengine.model.response.InvestmentEvaluationResponse;
import com.mteam.rebuildengine.model.response.InvestmentSnapshot;

import java.util.Optional;

public interface InvestmentService {
    Optional<InvestmentEvaluationResponse> evaluate(String buildingId);

    Optional<InvestmentSnapshot> computeSnapshot(String buildingId);

    // 배치가 페이지 단위로 미리 벌크 조회한 BuildingDataBundle과, 배치 시작 시 1회 로드한
    // TradeStatsIndex(F-08 유사거래 인덱스)까지 함께 넘겨 건물별 DB 재조회를 완전히 피한다
    // (F-06/F-07/F-08 계산 로직 자체는 라이브 단건 조회와 동일).
    InvestmentSnapshot computeSnapshot(BuildingEntity building, BuildingDataBundle bundle, TradeStatsIndex tradeStatsIndex);

    // §3.4 — investment_result에 저장된 스냅샷을 읽기만 한다(실시간 재계산 아님). F-05/F-10용.
    Optional<BuildingAnalysisResponse> getStoredAnalysis(String buildingId);
}
