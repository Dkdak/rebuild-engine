package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.model.response.BuildingInfoResponse;
import com.mteam.rebuildengine.service.search.BuildingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FEATURE_05_PROPERTY_INFO.md §2.1 "건물정보" 그룹(대지면적·연면적·층수·건폐율·용적률·사용승인일 등) —
// F-05가 이미 갖고 있던 BuildingService.findByBdrgSn을 buildingId 단건 조회 엔드포인트로 노출한다
// (서비스 로직 재사용, 신규 계산 없음). market/remodeling/building-summary와 동일한 "리스트는 요약만,
// 상세는 buildingId로 개별 조회" 원칙 — PropertyResponse(리스트)엔 이 필드들을 넣지 않는다(2026-08-08).
@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
public class PropertyDetailController {

    private final BuildingService buildingService;

    @GetMapping("/{buildingId}")
    public ResponseEntity<BuildingInfoResponse> detail(@PathVariable String buildingId) {
        return buildingService.findByBdrgSn(buildingId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
