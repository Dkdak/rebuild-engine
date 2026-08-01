package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.service.LandPriceCsvConverterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

// FEATURE_16_PRICE_DATA_MIGRATION.md §3.2 — 개별공시지가정보(서울) 원본을 정규화한다. DB에는 직접
// 안 쓰고 CSV만 만든다 — 실제 적재는 postgres/sql/load_land_price_csv.sql.
@RestController
@RequestMapping("/api/v1/admin/land-price")
@RequiredArgsConstructor
public class AdminLandPriceController {

    private final LandPriceCsvConverterService landPriceCsvConverterService;

    @PostMapping("/export")
    public ResponseEntity<LandPriceCsvConverterService.ConvertResult> export() throws IOException {
        return ResponseEntity.ok(landPriceCsvConverterService.convert());
    }
}
