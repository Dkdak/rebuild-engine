package com.mteam.rebuildengine.model.request;

import java.math.BigDecimal;
import java.util.List;

// FEATURE_04 §3.1 POST /api/v1/properties/search 요청 body. bjdongCd/buildingId 동시 전달 불가, 둘 다
// 없으면 위치 기본값 중구 적용(§0-C). propertyTypeFilters가 비어 있으면 유형/면적 제한 없음(§2.4 "전체").
// 유형별로 면적 단위가 달라(§2.1-a) areaMin/Max를 필터마다 따로 받는다 — 배열 구조라 GET 쿼리보다
// POST body가 자연스러워 전환했다(§5.1 Open Item 결정, 2026-07-28).
public record PropertySearchRequest(
        String bjdongCd,
        String buildingId,
        Integer buildYearMin,
        Integer buildYearMax,
        List<PropertyTypeFilter> propertyTypeFilters,
        Integer page,
        Integer size
) {
    public record PropertyTypeFilter(String type, BigDecimal areaMin, BigDecimal areaMax) {
    }
}
