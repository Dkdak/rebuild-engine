package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.model.response.CostEstimationResponse;
import com.mteam.rebuildengine.service.analysis.CostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FEATURE_07_COST.md §3 — F-05 "공사비" 카드가 매물 선택 시 호출(F-06/F-08/F-17과 동일한
// /api/v1/properties/{buildingId}/* 패턴).
@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
public class CostController {

    private final CostService costService;

    @GetMapping("/{buildingId}/cost")
    public ResponseEntity<CostEstimationResponse> cost(@PathVariable String buildingId) {
        return costService.getCostEstimation(buildingId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
