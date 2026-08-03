package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.model.response.MarketAnalysisResponse;
import com.mteam.rebuildengine.service.MarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FEATURE_08_MARKET.md §3.6 — F-05 "시세분석" 섹션이 매물 선택 시 호출.
@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
public class MarketController {

    private final MarketService marketService;

    @GetMapping("/{buildingId}/market")
    public ResponseEntity<MarketAnalysisResponse> market(@PathVariable String buildingId) {
        return marketService.getMarketAnalysis(buildingId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
