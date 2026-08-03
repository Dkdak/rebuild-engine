package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.ApartmentPriceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApartmentPriceRepository extends JpaRepository<ApartmentPriceEntity, Long> {
    // F-08 §3.6 "공시가격" — 한 건물(bdrg_sn)에 세대별로 여러 행이 매칭될 수 있어(구분소유), 전부 받아
    // Service가 최신 연/월 행만 평균한다.
    List<ApartmentPriceEntity> findByBuildingId(String buildingId);

    // F-09 V1 배치(InvestmentAnalysisBatchService) — 페이지 단위 벌크 조회, building_id별로 그루핑해 사용.
    List<ApartmentPriceEntity> findByBuildingIdIn(List<String> buildingIds);
}
