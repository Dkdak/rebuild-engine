package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.service.datapipeline.TradeBuildingMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// trade <-> building 매칭 계산 -> CSV 출력 수동 트리거 (F-15 §3.4). DB에는 직접 안 쓰고 CSV만 만든다 —
// 실제 trade.building_id 반영은 postgres/sql/load_trade_building_mapping.sql.
// building-gis-mapping(F-14, GIS 좌표 전담)과는 소스 테이블·매칭 알고리즘이 달라 별도 엔드포인트로 분리.
@RestController
@RequestMapping("/api/v1/admin/trade-building-mapping")
@RequiredArgsConstructor
public class AdminTradeBuildingMappingController {

    private final TradeBuildingMappingService tradeBuildingMappingService;

    @PostMapping("/export")
    public ResponseEntity<TradeBuildingMappingService.MatchResult> export() {
        return ResponseEntity.ok(tradeBuildingMappingService.exportMatchingCsv());
    }
}
