package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.service.datapipeline.PermitCsvConverterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

// FEATURE_06_REMODELING.md §3.3 — 건축인허가정보 원본을 정규화한다. DB에는 직접 안 쓰고 CSV만
// 만든다 — 실제 적재는 postgres/sql/load_permit_csv.sql(source_file 단위 교체, trade와 동일 패턴).
@RestController
@RequestMapping("/api/v1/admin/permit")
@RequiredArgsConstructor
public class AdminPermitController {

    private final PermitCsvConverterService permitCsvConverterService;

    // rawFileName 예: 인허가_20260801054906.csv (postgres/data/raw/permit/ 기준)
    @PostMapping("/export")
    public ResponseEntity<PermitCsvConverterService.ConvertResult> export(
            @RequestParam String rawFileName) throws IOException {
        return ResponseEntity.ok(permitCsvConverterService.convert(rawFileName));
    }
}
