package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.service.datapipeline.BuildingSummaryCsvConverterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

// FEATURE_17_BUILDING_SUMMARY_MIGRATION.md §3.2 — 건축물대장 총괄표제부 원본을 정규화한다. DB에는
// 직접 안 쓰고 CSV만 만든다 — 실제 적재는 postgres/sql/load_building_summary_csv.sql.
@RestController
@RequestMapping("/api/v1/admin/building-summary")
@RequiredArgsConstructor
public class AdminBuildingSummaryController {

    private final BuildingSummaryCsvConverterService buildingSummaryCsvConverterService;

    @PostMapping("/export")
    public ResponseEntity<BuildingSummaryCsvConverterService.ConvertResult> export() throws IOException {
        return ResponseEntity.ok(buildingSummaryCsvConverterService.convert());
    }
}
