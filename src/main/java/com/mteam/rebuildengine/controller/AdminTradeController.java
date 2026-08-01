package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.service.TradeCsvConverterService;
import com.mteam.rebuildengine.utils.PropertyType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// FEATURE_15_TRADE_DATA_MIGRATION.md §3.3 — data/raw/trade/ 원본 CSV를 통합 스키마 UTF-8 CSV 하나로
// 정규화한다. DB에는 직접 안 쓰고 CSV만 만든다 — 실제 적재는 postgres/sql/load_trade_csv.sql.
@RestController
@RequestMapping("/api/v1/admin/trade")
@RequiredArgsConstructor
public class AdminTradeController {

    private final TradeCsvConverterService tradeCsvConverterService;

    // propertyTypes 생략 시 6종 전부. 지정하면(예: 상업업무용,공장창고) 그 유형의 원본 파일만 변환 —
    // load_trade_csv.sql이 source_file 단위로 교체하므로 나머지 유형의 기존 행은 건드리지 않는다.
    @PostMapping("/export")
    public ResponseEntity<TradeCsvConverterService.ConvertResult> export(
            @RequestParam(required = false) List<String> propertyTypes) throws IOException {
        Set<PropertyType> typesFilter = propertyTypes == null ? Set.of()
                : propertyTypes.stream().map(AdminTradeController::resolvePropertyType).collect(Collectors.toSet());
        return ResponseEntity.ok(tradeCsvConverterService.convert(typesFilter));
    }

    private static PropertyType resolvePropertyType(String label) {
        return PropertyType.fromLabel(label)
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 propertyType 값입니다: " + label));
    }
}
