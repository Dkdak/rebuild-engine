package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.LanduseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LanduseRepository extends JpaRepository<LanduseEntity, Long> {
    // F-06 §3.2-2 — 한 필지에 지정이 여러 개면 방어적으로 List, Service가 첫 값을 쓴다(ApartmentPriceRepository와 동일 이유).
    List<LanduseEntity> findByBuildingId(String buildingId);

    // F-09 V1 배치(InvestmentAnalysisBatchService) — 페이지 단위 벌크 조회, building_id별로 그루핑해 사용.
    List<LanduseEntity> findByBuildingIdIn(List<String> buildingIds);
}
