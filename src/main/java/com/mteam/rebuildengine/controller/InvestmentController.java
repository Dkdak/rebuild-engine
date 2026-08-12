package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.model.response.BuildingAnalysisResponse;
import com.mteam.rebuildengine.model.response.InvestmentEvaluationResponse;
import com.mteam.rebuildengine.service.analysis.InvestmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FEATURE_09_INVESTMENT.md §3.2/§3.4.
@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
public class InvestmentController {

    private final InvestmentService investmentService;

    // §3.2 — 실시간 재계산(관리자/디버깅용으로 유지, F-05/F-10은 더 이상 호출하지 않음).
    @GetMapping("/{buildingId}/investment")
    public ResponseEntity<InvestmentEvaluationResponse> investment(@PathVariable String buildingId) {
        return investmentService.evaluate(buildingId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // §3.4 — investment_result 스냅샷을 그대로 반환(F-05/F-10용, 실시간 재계산 아님).
    @GetMapping("/{buildingId}/analysis")
    public ResponseEntity<BuildingAnalysisResponse> analysis(@PathVariable String buildingId) {
        return investmentService.getStoredAnalysis(buildingId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
