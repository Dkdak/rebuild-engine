package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.service.datapipeline.BuildingGisMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// building <-> gis_building 매칭 계산 -> CSV 출력 수동 트리거 (F-14 §3.5). DB에는 직접 안 쓰고
// CSV만 만든다 — 실제 building_gis_mapping 반영은 postgres/sql/load_building_gis_mapping_csv.sql.
@RestController
@RequestMapping("/api/v1/admin/building-gis-mapping")
@RequiredArgsConstructor
public class AdminBuildingGisMappingController {

    private final BuildingGisMappingService buildingGisMappingService;

    @PostMapping("/export")
    public ResponseEntity<BuildingGisMappingService.MatchResult> export() {
        return ResponseEntity.ok(buildingGisMappingService.exportMatchingCsv());
    }
}
