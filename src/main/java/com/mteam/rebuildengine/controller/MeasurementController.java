package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.model.request.MeasurementStepSaveRequest;
import com.mteam.rebuildengine.model.response.MeasurementDetailResponse;
import com.mteam.rebuildengine.model.response.MeasurementHistoryEntryResponse;
import com.mteam.rebuildengine.model.response.MeasurementListItemResponse;
import com.mteam.rebuildengine.model.response.MeasurementStepSaveResponse;
import com.mteam.rebuildengine.model.response.ZoningLimitResponse;
import com.mteam.rebuildengine.service.analysis.MeasurementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// FEATURE_19_PERSONALIZED_ANALYSIS.md §3.2 — F-19 실측 입력. 전 API 로그인 필수(SecurityConfig).
@RestController
@RequestMapping("/api/v1/analysis/measurements")
@RequiredArgsConstructor
public class MeasurementController {

    private final MeasurementService measurementService;

    @GetMapping
    public ResponseEntity<List<MeasurementListItemResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(measurementService.list(authentication.getName()));
    }

    // §2.2-e — STEP1 용도지역 드롭다운용, 사용자 무관(zoning_limit 참조 테이블 그대로).
    @GetMapping("/zoning-limits")
    public ResponseEntity<List<ZoningLimitResponse>> zoningLimits() {
        return ResponseEntity.ok(measurementService.listZoningLimits());
    }

    @GetMapping("/{buildingId}")
    public ResponseEntity<MeasurementDetailResponse> get(Authentication authentication, @PathVariable String buildingId) {
        return measurementService.get(authentication.getName(), buildingId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{buildingId}/steps/{stepNo}")
    public ResponseEntity<MeasurementStepSaveResponse> saveStep(Authentication authentication, @PathVariable String buildingId,
                                                                  @PathVariable int stepNo, @RequestBody MeasurementStepSaveRequest request) {
        return ResponseEntity.ok(measurementService.saveStep(authentication.getName(), buildingId, stepNo, request));
    }

    @GetMapping("/{buildingId}/history")
    public ResponseEntity<List<MeasurementHistoryEntryResponse>> history(Authentication authentication, @PathVariable String buildingId) {
        return ResponseEntity.ok(measurementService.history(authentication.getName(), buildingId));
    }

    @DeleteMapping("/{buildingId}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable String buildingId) {
        measurementService.delete(authentication.getName(), buildingId);
        return ResponseEntity.noContent().build();
    }
}
