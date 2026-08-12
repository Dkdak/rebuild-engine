package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.service.datapipeline.DetachedHousePriceCsvConverterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

// FEATURE_16_PRICE_DATA_MIGRATION.md §5.1 — 개별주택가격정보(단독주택·다가구주택, 서울) 원본을
// 정규화한다. DB에는 직접 안 쓰고 CSV만 만든다 — 실제 적재는 postgres/sql/load_detached_house_price_csv.sql.
@RestController
@RequestMapping("/api/v1/admin/detached-house-price")
@RequiredArgsConstructor
public class AdminDetachedHousePriceController {

    private final DetachedHousePriceCsvConverterService detachedHousePriceCsvConverterService;

    @PostMapping("/export")
    public ResponseEntity<DetachedHousePriceCsvConverterService.ConvertResult> export() throws IOException {
        return ResponseEntity.ok(detachedHousePriceCsvConverterService.convert());
    }
}
