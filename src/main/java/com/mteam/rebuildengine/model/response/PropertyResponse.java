package com.mteam.rebuildengine.model.response;

import com.mteam.rebuildengine.utils.PropertyType;
import com.mteam.rebuildengine.utils.PropertyTypeClassifier;

import java.math.BigDecimal;

// FEATURE_04 §3.1 GET /api/v1/properties/search 항목 스키마. 1차는 price/grade/roi가 null 고정(F-09 미구현).
// propertyType은 PropertyTypeClassifier(DOMAIN.md §1 6종, 2026-07-28 결정)로 분류 — 매핑 안 되는 건물
// (오피스텔 등)은 null. area는 연면적(grossFloorArea) 기준으로 채운다.
public record PropertyResponse(
        String id,
        String propertyType,
        String address,
        BigDecimal price,
        BigDecimal area,
        Integer buildYear,
        BigDecimal lat,
        BigDecimal lng,
        String grade,
        BigDecimal roi
) {
    public static PropertyResponse from(BuildingInfoResponse building) {
        Integer buildYear = building.useApprovalDate() != null ? building.useApprovalDate().getYear() : null;
        String propertyType = PropertyTypeClassifier.classify(building.mainUsageNm(), building.groundFloors())
                .map(PropertyType::label)
                .orElse(null);
        return new PropertyResponse(
                building.bdrgSn(),
                propertyType,
                building.platPlc(),
                null,
                building.grossFloorArea(),
                buildYear,
                building.lat(),
                building.lng(),
                null,
                null
        );
    }
}
