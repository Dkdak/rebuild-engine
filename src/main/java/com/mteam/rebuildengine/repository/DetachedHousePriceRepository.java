package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.DetachedHousePriceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DetachedHousePriceRepository extends JpaRepository<DetachedHousePriceEntity, Long> {
    // F-08 §3.6 "공시가격"(단독/다가구) — LandPriceRepository와 동일 이유로 List로 받아
    // Service가 최신 연/월 행만 평균한다.
    List<DetachedHousePriceEntity> findByBuildingId(String buildingId);

    // F-09 V1 배치(InvestmentAnalysisBatchService) — 페이지 단위 벌크 조회, building_id별로 그루핑해 사용.
    List<DetachedHousePriceEntity> findByBuildingIdIn(List<String> buildingIds);
}
