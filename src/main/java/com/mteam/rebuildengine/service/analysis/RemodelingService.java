package com.mteam.rebuildengine.service.analysis;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.model.response.RemodelingResultResponse;

import java.util.Optional;

public interface RemodelingService {
    Optional<RemodelingResultResponse> getRemodelingResult(String buildingId);

    // F-09 V1 배치(InvestmentAnalysisBatchService) 전용 — 이미 조회된 BuildingEntity와 페이지 단위
    // 벌크 조회 결과(BuildingDataBundle)를 그대로 사용해 건물별 재조회를 피한다.
    RemodelingResultResponse getRemodelingResult(BuildingEntity building, BuildingDataBundle bundle);
}
