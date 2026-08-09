package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.SiteBoundaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SiteBoundaryRepository extends JpaRepository<SiteBoundaryEntity, Long> {
    // gis_building.pnu로 조인 — F-05 findByBdrgSn(단건) 전용, is_deleted 필터는 서비스 레이어에서
    // gis_building 매칭 자체가 없으면 이미 null 처리되므로 여기선 필요 최소한만.
    Optional<SiteBoundaryEntity> findByPnuAndIsDeletedFalse(String pnu);
}
