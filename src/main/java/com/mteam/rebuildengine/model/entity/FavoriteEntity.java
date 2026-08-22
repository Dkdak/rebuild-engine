package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// F-11 관심목록(FEATURE_11_FAVORITES.md §3.1) — user_id+building_id UNIQUE, 해제는 소프트 삭제.
// gradeAtSave/roiAtSave는 등록 시점 investment_result 스냅샷 — investment_result가 building_id PK를
// 배치마다 덮어써 과거 값이 없는 문제를 관심목록에 담긴 매물에 한해 우회한다("등급 변화 배지"의 근거).
// app_user와 동일하게 SQL 스크립트 없이 Hibernate ddl-auto:update가 테이블을 생성·관리한다.
@Entity
@Table(name = "favorite",
        uniqueConstraints = @UniqueConstraint(name = "uk_favorite_user_building", columnNames = {"user_id", "building_id"}),
        indexes = @Index(name = "idx_favorite_user_active", columnList = "user_id, is_deleted, created_at DESC"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FavoriteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "building_id", nullable = false, length = 50)
    private String buildingId;

    // investment_result 미매칭(배치 전 신규 건물 등)이면 null — "정보 없음"과 구분하지 않는다(§3.1 범위 밖).
    @Column(name = "grade_at_save", length = 2)
    private String gradeAtSave;

    @Column(name = "roi_at_save")
    private BigDecimal roiAtSave;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @Column(nullable = false)
    private boolean isDeleted;

    @Builder
    public FavoriteEntity(Long userId, String buildingId, String gradeAtSave, BigDecimal roiAtSave) {
        this.userId = userId;
        this.buildingId = buildingId;
        this.gradeAtSave = gradeAtSave;
        this.roiAtSave = roiAtSave;
        this.isDeleted = false;
    }

    // 해제했다 다시 담은 건 새로 담은 것이다(§3.1) — 재등록 시점 값으로 갱신, 새 행을 만들지 않아
    // UNIQUE 제약과도 부딪히지 않는다.
    public void reactivate(String gradeAtSave, BigDecimal roiAtSave) {
        this.gradeAtSave = gradeAtSave;
        this.roiAtSave = roiAtSave;
        this.isDeleted = false;
    }

    public void markDeleted() {
        this.isDeleted = true;
    }

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
