package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BuildingRepository extends JpaRepository<BuildingEntity, Long> {
    Optional<BuildingEntity> findByBdrgSn(String bdrgSn);
}
