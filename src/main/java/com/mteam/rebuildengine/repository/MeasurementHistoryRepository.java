package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.MeasurementHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeasurementHistoryRepository extends JpaRepository<MeasurementHistoryEntity, Long> {
    // FEATURE_19 §3.2 GET .../history — 최신 변경순.
    List<MeasurementHistoryEntity> findByUserIdAndBuildingIdOrderByChangedAtDesc(Long userId, String buildingId);
}
