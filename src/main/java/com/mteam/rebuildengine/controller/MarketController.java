package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.model.response.AgeAdjustedPriceResponse;
import com.mteam.rebuildengine.model.response.MarketAnalysisResponse;
import com.mteam.rebuildengine.service.analysis.MarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    // FEATURE_19_PERSONALIZED_ANALYSIS.md §2.2-a STEP5 "유효연식 참고표" — F-19 실측 화면이 소비할
    // 예정(§3.7 확장, 2026-08-24). 건물이 없거나 리모델링 "불가" 판정이면 빈 배열.
    @GetMapping("/{buildingId}/market/age-adjusted")
    public ResponseEntity<List<AgeAdjustedPriceResponse>> ageAdjustedPrice(@PathVariable String buildingId) {
        return ResponseEntity.ok(marketService.getPostRemodelPriceByAge(buildingId));
    }
}
