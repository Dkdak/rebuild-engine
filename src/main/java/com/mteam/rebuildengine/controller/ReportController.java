package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.model.response.BuildingReportResponse;
import com.mteam.rebuildengine.service.analysis.MeasurementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FEATURE_19_PERSONALIZED_ANALYSIS.md §1.1 — F-10 리포트탭. 로그인 필수(SecurityConfig) — 지도 탭만
// 비로그인, 리포트탭은 CASE1/CASE2 어느 쪽이든 로그인 계정 기준이라 게스트 열람 대상이 아니다.
@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
public class ReportController {

    private final MeasurementService measurementService;

    @GetMapping("/{buildingId}/report")
    public ResponseEntity<BuildingReportResponse> report(Authentication authentication, @PathVariable String buildingId) {
        return ResponseEntity.ok(measurementService.getReport(authentication.getName(), buildingId));
    }
}
