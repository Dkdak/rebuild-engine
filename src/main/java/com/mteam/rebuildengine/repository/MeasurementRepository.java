package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.MeasurementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeasurementRepository extends JpaRepository<MeasurementEntity, Long> {
    Optional<MeasurementEntity> findByUserIdAndBuildingIdAndIsDeletedFalse(Long userId, String buildingId);

    // 소프트 삭제된 행 재사용(재측정 시 reactivate) — FavoriteRepository와 동일 패턴.
    Optional<MeasurementEntity> findByUserIdAndBuildingId(Long userId, String buildingId);

    // FEATURE_19 §3.2-a 목록 API — 미시작은 이 목록에 안 나온다(실측 레코드 자체가 없음, 프론트가
    // F-11 관심목록과 buildingId로 매칭해 3분류를 만든다).
    List<MeasurementEntity> findByUserIdAndIsDeletedFalse(Long userId);
}
