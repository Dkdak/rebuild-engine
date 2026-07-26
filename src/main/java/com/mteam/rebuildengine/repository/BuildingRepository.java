package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingRepository extends JpaRepository<BuildingEntity, String> {
}
