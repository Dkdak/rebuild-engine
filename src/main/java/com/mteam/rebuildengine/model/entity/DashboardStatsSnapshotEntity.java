package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

// F-03 대시보드 "리모델링 후보" 집계 스냅샷(2026-08-23, product 결정) — F-09 배치가 552,957건을
// 순회하며 부산물로 집계한 결과를 매 실행마다 새 행으로 추가(append-only, 절대 UPDATE 안 함 —
// 배치 실행 간 추세 비교를 나중에 가능하게 하려는 의도적 설계). 주간 안전망 기준으로도 연 52행뿐이라
// 저장 비용은 무시할 만하다. investment_result처럼 Java에서 save()하지 않고 SQL(load_dashboard_
// stats_snapshot.sql)로만 채운다 — 이 엔티티는 최신 행 조회 전용.
@Entity
@Table(name = "dashboard_stats_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DashboardStatsSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    // DashboardStatsResponse를 그대로 직렬화한 JSON — 응답 스키마를 그대로 재사용한다(product 결정 #2).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stats", nullable = false, columnDefinition = "jsonb")
    private String stats;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
