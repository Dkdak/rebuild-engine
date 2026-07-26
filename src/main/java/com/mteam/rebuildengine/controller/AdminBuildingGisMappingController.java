package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.service.BuildingGisMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// building <-> gis_building 매칭 배치 수동 트리거 (F-14)
@RestController
@RequestMapping("/api/v1/admin/building-gis-mapping")
@RequiredArgsConstructor
public class AdminBuildingGisMappingController {

    private final BuildingGisMappingService buildingGisMappingService;

    @PostMapping("/run")
    public ResponseEntity<BuildingGisMappingService.MatchResult> run() {
        return ResponseEntity.ok(buildingGisMappingService.runMatching());
    }
}
