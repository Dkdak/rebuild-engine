package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.entity.ApartmentPriceEntity;
import com.mteam.rebuildengine.model.entity.DetachedHousePriceEntity;
import com.mteam.rebuildengine.model.entity.LandPriceEntity;
import com.mteam.rebuildengine.model.entity.LanduseDistrictEntity;
import com.mteam.rebuildengine.model.entity.LanduseEntity;
import com.mteam.rebuildengine.model.entity.PermitEntity;
import com.mteam.rebuildengine.model.entity.TradeEntity;

import java.util.List;
import java.util.Map;

// F-09 V1 배치(InvestmentAnalysisBatchService) 전용 — 건물 1건마다 따로 조회하던 permit/landuse/
// landuse_district/apartment_price/land_price/trade를 페이지(1000건) 단위로 building_id IN (...)
// 벌크 조회해 그루핑한 값. RemodelingServiceImpl/MarketServiceImpl이 이 번들이 있으면 그걸 쓰고,
// 없으면(라이브 단건 조회) 기존처럼 건물별로 조회한다 — 계산 로직 자체는 완전히 동일, 조회 방식만 다르다.
public record BuildingDataBundle(
        Map<String, List<PermitEntity>> permitsByBuildingId,
        Map<String, List<LanduseEntity>> landuseByBuildingId,
        Map<String, List<LanduseDistrictEntity>> landuseDistrictByBuildingId,
        Map<String, List<ApartmentPriceEntity>> apartmentPriceByBuildingId,
        Map<String, List<LandPriceEntity>> landPriceByBuildingId,
        Map<String, List<DetachedHousePriceEntity>> detachedHousePriceByBuildingId,
        Map<String, List<TradeEntity>> recentTradeByBuildingId
) {
    public List<PermitEntity> permits(String buildingId) {
        return permitsByBuildingId.getOrDefault(buildingId, List.of());
    }

    public List<LanduseEntity> landuse(String buildingId) {
        return landuseByBuildingId.getOrDefault(buildingId, List.of());
    }

    public List<LanduseDistrictEntity> landuseDistricts(String buildingId) {
        return landuseDistrictByBuildingId.getOrDefault(buildingId, List.of());
    }

    public List<ApartmentPriceEntity> apartmentPrices(String buildingId) {
        return apartmentPriceByBuildingId.getOrDefault(buildingId, List.of());
    }

    public List<LandPriceEntity> landPrices(String buildingId) {
        return landPriceByBuildingId.getOrDefault(buildingId, List.of());
    }

    public List<DetachedHousePriceEntity> detachedHousePrices(String buildingId) {
        return detachedHousePriceByBuildingId.getOrDefault(buildingId, List.of());
    }

    public List<TradeEntity> recentTrades(String buildingId) {
        return recentTradeByBuildingId.getOrDefault(buildingId, List.of());
    }
}
