package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.service.datapipeline.LanduseCsvConverterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

// FEATURE_06_REMODELING.md §3.3 — 토지이용계획정보(서울, 법정 16개 용도지역만 필터링) 원본을
// 정규화한다. DB에는 직접 안 쓰고 CSV만 만든다 — 실제 적재는 postgres/sql/load_landuse_csv.sql.
@RestController
@RequestMapping("/api/v1/admin/landuse")
@RequiredArgsConstructor
public class AdminLanduseController {

    private final LanduseCsvConverterService landuseCsvConverterService;

    @PostMapping("/export")
    public ResponseEntity<LanduseCsvConverterService.ConvertResult> export() throws IOException {
        return ResponseEntity.ok(landuseCsvConverterService.convert());
    }
}
