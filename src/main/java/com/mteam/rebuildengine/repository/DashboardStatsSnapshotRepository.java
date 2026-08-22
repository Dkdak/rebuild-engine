package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.DashboardStatsSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DashboardStatsSnapshotRepository extends JpaRepository<DashboardStatsSnapshotEntity, Long> {
    // F-03 대시보드 조회 — append-only 테이블의 최신 1행만 읽는다(computed_at 기준).
    Optional<DashboardStatsSnapshotEntity> findTopByOrderByComputedAtDesc();
}
