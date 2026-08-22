package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.LanduseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LanduseRepository extends JpaRepository<LanduseEntity, Long> {
    // F-06 §3.2-2 — 한 필지에 지정이 여러 개면 방어적으로 List, Service가 첫 값을 쓴다(ApartmentPriceRepository와 동일 이유).
    List<LanduseEntity> findByBuildingId(String buildingId);

    // F-09 V1 배치(InvestmentAnalysisBatchService) — 페이지 단위 벌크 조회, building_id별로 그루핑해 사용.
    List<LanduseEntity> findByBuildingIdIn(List<String> buildingIds);

    // F-03 대시보드 dataStatus "용도지역 매칭" — 건물당 여러 행일 수 있어 매칭된 건물 수는 distinct
    // building_id 카운트여야 한다(단순 count()는 건물 수가 아니라 행 수가 되어 과대집계됨).
    @Query("SELECT COUNT(DISTINCT l.buildingId) FROM LanduseEntity l")
    long countDistinctBuildingId();
}
