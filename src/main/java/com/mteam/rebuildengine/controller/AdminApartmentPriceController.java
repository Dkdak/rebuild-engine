package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.service.ApartmentPriceCsvConverterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

// FEATURE_16_PRICE_DATA_MIGRATION.md §3.2 — 공동주택가격정보(전국) 원본을 서울만 걸러 정규화한다.
// DB에는 직접 안 쓰고 CSV만 만든다 — 실제 적재는 postgres/sql/load_apartment_price_csv.sql.
@RestController
@RequestMapping("/api/v1/admin/apartment-price")
@RequiredArgsConstructor
public class AdminApartmentPriceController {

    private final ApartmentPriceCsvConverterService apartmentPriceCsvConverterService;

    @PostMapping("/export")
    public ResponseEntity<ApartmentPriceCsvConverterService.ConvertResult> export() throws IOException {
        return ResponseEntity.ok(apartmentPriceCsvConverterService.convert());
    }
}
