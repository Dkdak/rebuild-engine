package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.LanduseDistrictEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LanduseDistrictRepository extends JpaRepository<LanduseDistrictEntity, Long> {
    // F-06 §2.1 — 한 건물에 지구/구역 지정이 여러 개일 수 있어 List로 받는다.
    List<LanduseDistrictEntity> findByBuildingId(String buildingId);

    // F-09 V1 배치(InvestmentAnalysisBatchService) — 페이지 단위 벌크 조회, building_id별로 그루핑해 사용.
    List<LanduseDistrictEntity> findByBuildingIdIn(List<String> buildingIds);
}
