package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.service.datapipeline.AddressBuildingMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FEATURE_16_PRICE_DATA_MIGRATION.md §3.3 — apartment_price/land_price/landuse/permit <-> building
// 매칭(구조화된 컬럼 정확일치+법정동명 pg_trgm 폴백, AddressBuildingMappingService 공유). DB에는
// 직접 안 쓰고 CSV만 만든다 — 실제 반영은 postgres/sql/load_{table}_building_mapping.sql.
@RestController
@RequiredArgsConstructor
public class AdminAddressBuildingMappingController {

    private final AddressBuildingMappingService addressBuildingMappingService;

    @PostMapping("/api/v1/admin/apartment-price-building-mapping/export")
    public ResponseEntity<AddressBuildingMappingService.MatchResult> exportApartmentPriceMapping() {
        return ResponseEntity.ok(addressBuildingMappingService.exportApartmentPriceMatchingCsv());
    }

    @PostMapping("/api/v1/admin/land-price-building-mapping/export")
    public ResponseEntity<AddressBuildingMappingService.MatchResult> exportLandPriceMapping() {
        return ResponseEntity.ok(addressBuildingMappingService.exportMatchingCsv(
                AddressBuildingMappingService.AddressTable.LAND_PRICE));
    }

    @PostMapping("/api/v1/admin/landuse-building-mapping/export")
    public ResponseEntity<AddressBuildingMappingService.MatchResult> exportLanduseMapping() {
        return ResponseEntity.ok(addressBuildingMappingService.exportMatchingCsv(
                AddressBuildingMappingService.AddressTable.LANDUSE));
    }

    @PostMapping("/api/v1/admin/permit-building-mapping/export")
    public ResponseEntity<AddressBuildingMappingService.MatchResult> exportPermitMapping() {
        return ResponseEntity.ok(addressBuildingMappingService.exportMatchingCsv(
                AddressBuildingMappingService.AddressTable.PERMIT));
    }

    @PostMapping("/api/v1/admin/building-summary-building-mapping/export")
    public ResponseEntity<AddressBuildingMappingService.MatchResult> exportBuildingSummaryMapping() {
        return ResponseEntity.ok(addressBuildingMappingService.exportMatchingCsv(
                AddressBuildingMappingService.AddressTable.BUILDING_SUMMARY));
    }

    @PostMapping("/api/v1/admin/landuse-district-building-mapping/export")
    public ResponseEntity<AddressBuildingMappingService.MatchResult> exportLanduseDistrictMapping() {
        return ResponseEntity.ok(addressBuildingMappingService.exportMatchingCsv(
                AddressBuildingMappingService.AddressTable.LANDUSE_DISTRICT));
    }

    @PostMapping("/api/v1/admin/detached-house-price-building-mapping/export")
    public ResponseEntity<AddressBuildingMappingService.MatchResult> exportDetachedHousePriceMapping() {
        return ResponseEntity.ok(addressBuildingMappingService.exportMatchingCsv(
                AddressBuildingMappingService.AddressTable.DETACHED_HOUSE_PRICE));
    }
}
