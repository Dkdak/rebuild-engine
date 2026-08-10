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
// recentTrade는 이 응답에 없다 — 매매 가능 건물의 11.5%만 존재할 정도로 희박하고, 카드 표시값도
// estimatedPrice로 대체돼 목록에선 불필요(F-05 상세 화면, GET /api/v1/properties/{buildingId}에서만 조회).
// 대지면적/건폐율/용적률은 이 응답(리스트)에 넣지 않는다(2026-08-08 결정) — F-05 "건물정보" 카드는
// GET /api/v1/properties/{buildingId}(신규, BuildingInfoResponse 그대로)로 별도 조회한다.
// market/remodeling/building-summary와 동일하게 "리스트는 요약만, 상세는 buildingId로 개별 조회" 원칙.
// remodelingVerdict(2026-08-08 추가): grade/roi와 같은 소스(investment_result)에서 하나 더 꺼낸다 —
// remodeling_basis JSON의 verdict만 뽑아서 노출, 새 계산 없음. "POSSIBLE"/"LIMITED"/"NOT_POSSIBLE"
// (RemodelingVerdict enum name 그대로), investment_result 미매칭이거나 저장값이 없으면 null.
// estimatedPrice(2026-08-09 추가, §2.1-h "카드 노출값 교체 결정") — grade/remodelingVerdict와 같은
// 소스(investment_result.market_basis)에서 F-08 estimatedPrice 그대로 꺼낸다(추가 계산 없음, F-08 §3.6
// 라이브 API와 완전히 같은 모양). ㎡당가격×건물 전체 면적으로 스케일이 항상 맞고 전 유형 공통이라
// recentTrade(개별 호실 거래가 건물 전체 가격처럼 보이는 착시)와 달리 이 문제가 없다.
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
        String remodelingVerdict,
        EstimatedPriceResponse estimatedPrice
) {
    public static PropertyResponse from(BuildingInfoResponse building, String grade, BigDecimal roi,
                                         String remodelingVerdict, EstimatedPriceResponse estimatedPrice) {
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
                remodelingVerdict,
                estimatedPrice
        );
    }
}
