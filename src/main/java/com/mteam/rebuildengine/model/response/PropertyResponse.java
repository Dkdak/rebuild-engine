package com.mteam.rebuildengine.model.response;

import com.mteam.rebuildengine.utils.PropertyType;
import com.mteam.rebuildengine.utils.PropertyTypeClassifier;

import java.math.BigDecimal;
import java.util.Optional;

// FEATURE_04 §3.1 POST /api/v1/properties/search 항목 스키마. price는 1차엔 null 고정(F-09 미구현).
// grade/roi는 F-09 정식 기획 전 investment_result 스파이크 테스트 더미데이터를 그대로 반영(§2.1-g).
// propertyType은 PropertyTypeClassifier(DOMAIN.md §1 6종)로 분류 — 매핑 안 되는 건물(오피스텔 등)은 null.
// area(메인 표시값, §2.1-e)는 아파트·연립다세대만 세대당 추정 면적(gfa/hh_cnt), 나머지는 건물 전체
// 면적 — totalBuildingArea는 항상 건물 전체 면적이라 area와 다를 때만(아파트·연립다세대) 보조 표시용.
// recentTrade(F-04 2차, §2.1-h)는 PropertyServiceImpl이 TradeRepository로 직접 조회해 파라미터로
// 넘긴다(2026-08-08 변경) — 전에는 BuildingInfoResponse.recentTrade()를 그대로 물려받아 F-04가
// BuildingService의 내부 계산에 의존했으나, 목록/상세 조회 경로를 독립시키기 위해 분리(FEATURE_04_SEARCH.md §5.1).
// 대지면적/건폐율/용적률은 이 응답(리스트)에 넣지 않는다(2026-08-08 결정) — F-05 "건물정보" 카드는
// GET /api/v1/properties/{buildingId}(신규, BuildingInfoResponse 그대로)로 별도 조회한다.
// market/remodeling/building-summary와 동일하게 "리스트는 요약만, 상세는 buildingId로 개별 조회" 원칙.
public record PropertyResponse(
        String id,
        String propertyType,
        String address,
        BigDecimal price,
        BigDecimal area,
        BigDecimal totalBuildingArea,
        Integer householdCount,
        Integer buildYear,
        BigDecimal lat,
        BigDecimal lng,
        String grade,
        BigDecimal roi,
        RecentTradeResponse recentTrade
) {
    public static PropertyResponse from(BuildingInfoResponse building, String grade, BigDecimal roi,
                                         RecentTradeResponse recentTrade) {
        Integer buildYear = building.useApprovalDate() != null ? building.useApprovalDate().getYear() : null;
        Optional<PropertyType> classified = PropertyTypeClassifier.classify(building.mainUsageNm(), building.groundFloors());
        String propertyType = classified.map(PropertyType::label).orElse(null);
        BigDecimal area = classified
                .map(type -> PropertyTypeClassifier.displayArea(type, building.grossFloorArea(), building.householdCount()))
                .orElse(building.grossFloorArea());
        Integer householdCount = classified
                .filter(type -> type == PropertyType.APARTMENT || type == PropertyType.ROW_HOUSE)
                .map(type -> building.householdCount())
                .orElse(null);
        return new PropertyResponse(
                building.bdrgSn(),
                propertyType,
                building.platPlc(),
                null,
                area,
                building.grossFloorArea(),
                householdCount,
                buildYear,
                building.lat(),
                building.lng(),
                grade,
                roi,
                recentTrade
        );
    }
}
