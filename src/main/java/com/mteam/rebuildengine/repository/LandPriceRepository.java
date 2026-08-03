package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.LandPriceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LandPriceRepository extends JpaRepository<LandPriceEntity, Long> {
    // F-08 §3.6 "토지당 가격" — 필지 하나에 보통 한 행이지만 방어적으로 List로 받아 Service가
    // 최신 연/월 행만 평균한다(ApartmentPriceRepository와 동일 이유).
    List<LandPriceEntity> findByBuildingId(String buildingId);

    // F-09 V1 배치(InvestmentAnalysisBatchService) — 페이지 단위 벌크 조회, building_id별로 그루핑해 사용.
    List<LandPriceEntity> findByBuildingIdIn(List<String> buildingIds);
}
