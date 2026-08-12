package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.model.response.BuildingSummaryResponse;
import com.mteam.rebuildengine.service.search.BuildingSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FEATURE_05_PROPERTY_INFO.md §2.1/§5.1 "단지 정보" 카드(공동주택 매물만, 프론트가 노출 여부 판단) —
// F-05 RightPanel이 매물 선택 시 호출(F-08 MarketController/F-06 RemodelingController와 동일 패턴).
@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
public class BuildingSummaryController {

    private final BuildingSummaryService buildingSummaryService;

    @GetMapping("/{buildingId}/building-summary")
    public ResponseEntity<BuildingSummaryResponse> buildingSummary(@PathVariable String buildingId) {
        return buildingSummaryService.getBuildingSummary(buildingId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
