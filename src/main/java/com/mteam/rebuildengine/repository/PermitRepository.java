package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.PermitEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermitRepository extends JpaRepository<PermitEntity, Long> {
    // F-06 §3.2-3 — 최신 허가일 순으로 받아 Service가 진행중 개발행위 판정·최근 이력 표시에 쓴다.
    List<PermitEntity> findByBuildingIdOrderByPermitDateDesc(String buildingId);

    // F-09 V1 배치(InvestmentAnalysisBatchService) — 페이지 단위 벌크 조회. 전체 결과가 permit_date
    // desc로 정렬되므로 building_id별로 그루핑해도 건물별 순서는 그대로 유지된다.
    List<PermitEntity> findByBuildingIdInOrderByPermitDateDesc(List<String> buildingIds);
}
