package com.mteam.rebuildengine.service.board;

import com.mteam.rebuildengine.model.response.DashboardStatsResponse;

import java.util.Optional;

public interface DashboardStatsService {
    // FEATURE_03_BOARD.md — 대시보드 "리모델링 후보" 집계, F-09 배치 스냅샷을 읽는다(2026-08-23 product
    // 확정, 배치+스냅샷). 스냅샷이 아직 없으면(최초 배포 직후 배치 미실행) empty.
    Optional<DashboardStatsResponse> getStats();
}
