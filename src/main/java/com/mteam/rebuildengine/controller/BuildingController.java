package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.model.response.BuildingTitleListResponse;
import com.mteam.rebuildengine.service.search.BuildingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// FEATURE_05 §3.1 — 샘플/검증 단계 조회 API. F-04 1차가 실거래가 연동 전까지 이 API를 그대로 재사용한다.
@RestController
@RequestMapping("/api/v1/buildings")
@RequiredArgsConstructor
public class BuildingController {

    private final BuildingService buildingService;

    @GetMapping("/title")
    public ResponseEntity<BuildingTitleListResponse> title(
            @RequestParam String sigunguCd,
            @RequestParam String bjdongCd,
            @RequestParam(required = false) String platGbCd,
            @RequestParam(required = false) String bun,
            @RequestParam(required = false) String ji,
            @RequestParam(defaultValue = "10") int numOfRows,
            @RequestParam(defaultValue = "1") int pageNo
    ) {
        return ResponseEntity.ok(
                buildingService.searchTitle(sigunguCd, bjdongCd, platGbCd, bun, ji, numOfRows, pageNo));
    }
}
