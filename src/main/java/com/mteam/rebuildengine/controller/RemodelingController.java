package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.model.response.RemodelingResultResponse;
import com.mteam.rebuildengine.service.RemodelingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FEATURE_06_REMODELING.md §3.2 — F-05 "리모델링 가능성" 섹션이 매물 선택 시 호출(F-08 MarketController와
// 동일한 /api/v1/properties/{buildingId}/* 패턴).
@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
public class RemodelingController {

    private final RemodelingService remodelingService;

    @GetMapping("/{buildingId}/remodeling")
    public ResponseEntity<RemodelingResultResponse> remodeling(@PathVariable String buildingId) {
        return remodelingService.getRemodelingResult(buildingId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
