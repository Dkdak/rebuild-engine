package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.model.response.CostEstimationResponse;
import com.mteam.rebuildengine.model.response.RemodelingResultResponse;

import java.util.Optional;

public interface CostService {
    Optional<CostEstimationResponse> getCostEstimation(String buildingId);

    // InvestmentService(F-09)가 이미 조회한 BuildingEntity·F-06 결과를 그대로 넘겨 중복 조회/재계산을
    // 피한다(배치 성능 실측 중 발견, 2026-08-08).
    CostEstimationResponse getCostEstimation(BuildingEntity building, RemodelingResultResponse remodeling);
}
