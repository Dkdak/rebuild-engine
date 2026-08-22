package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.FavoriteEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<FavoriteEntity, Long> {
    // 재등록 시 기존 행(소프트 삭제 포함) 재사용 여부 판단 — isDeleted 무관하게 조회(FEATURE_11 §3.1 reactivate).
    Optional<FavoriteEntity> findByUserIdAndBuildingId(Long userId, String buildingId);

    // FEATURE_11 §2.1-a 기본 정렬(최근 담은 순).
    Page<FavoriteEntity> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // GET /api/v1/favorites/ids(§3.2) — 프론트가 ♥ 표시용 Set으로 들고 있을 buildingId만.
    @Query("SELECT f.buildingId FROM FavoriteEntity f WHERE f.userId = :userId AND f.isDeleted = false ORDER BY f.createdAt DESC")
    List<String> findActiveBuildingIdsByUserId(Long userId);
}
