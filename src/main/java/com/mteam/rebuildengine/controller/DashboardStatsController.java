package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.model.response.DashboardStatsResponse;
import com.mteam.rebuildengine.service.board.DashboardStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FEATURE_03_BOARD.md — 대시보드 "리모델링 후보" 집계, F-09 배치 스냅샷 조회(2026-08-23 product 확정,
// 배치+스냅샷). 개인 데이터가 아니라 인증 불필요(SecurityConfig 기본 permitAll).
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardStatsController {

    private final DashboardStatsService dashboardStatsService;

    @GetMapping("/candidate-stats")
    public ResponseEntity<DashboardStatsResponse> candidateStats() {
        // 최초 배포 직후 배치가 아직 한 번도 안 돌았으면 스냅샷이 없다 — 404로 명확히 구분.
        return dashboardStatsService.getStats()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
