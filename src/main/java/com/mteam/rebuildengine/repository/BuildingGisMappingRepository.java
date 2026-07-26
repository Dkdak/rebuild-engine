package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.BuildingGisMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BuildingGisMappingRepository extends JpaRepository<BuildingGisMappingEntity, Long> {
    Optional<BuildingGisMappingEntity> findByBuildingId(String buildingId);

    List<BuildingGisMappingEntity> findByBuildingIdIn(List<String> buildingIds);
}
