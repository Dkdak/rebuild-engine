package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.InvestmentResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvestmentResultRepository extends JpaRepository<InvestmentResultEntity, String> {
    List<InvestmentResultEntity> findByBuildingIdInAndIsDeletedFalse(List<String> buildingIds);
}
