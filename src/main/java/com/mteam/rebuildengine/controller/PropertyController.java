package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.model.request.PropertySearchRequest;
import com.mteam.rebuildengine.model.response.PropertySearchResponse;
import com.mteam.rebuildengine.service.PropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FEATURE_04 §3.1 — 통합 검색 후보 선택(bjdongCd/buildingId, 둘 다 없으면 §0-C 기본값 중구) +
// buildYear/propertyTypeFilters(유형별 면적, §2.1-a) 필터. propertyTypeFilters가 배열 구조라
// GET 쿼리 대신 POST body로 받는다(§5.1 Open Item 결정, 2026-07-28).
@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    @PostMapping("/search")
    public ResponseEntity<PropertySearchResponse> search(@RequestBody PropertySearchRequest request) {
        return ResponseEntity.ok(propertyService.search(request));
    }
}
