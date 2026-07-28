package com.mteam.rebuildengine.model.response;

import java.util.List;

// FEATURE_04 §3.1 GET /api/v1/properties/search 응답 스키마. totalPages는 프론트 페이지 목록 렌더링용
// (2026-07-27 추가 — totalCount/size로부터 계산 가능하지만 프론트에서 매번 계산하지 않도록 응답에 포함).
public record PropertySearchResponse(
        List<PropertyResponse> items,
        List<GradeSummaryResponse> gradeSummary,
        long totalCount,
        int page,
        int size,
        int totalPages
) {
    public static PropertySearchResponse of(BuildingTitleListResponse buildings, int page, int size) {
        List<PropertyResponse> items = buildings.items().stream().map(PropertyResponse::from).toList();
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) buildings.totalCount() / size);
        return new PropertySearchResponse(items, GradeSummaryResponse.emptySummary(),
                buildings.totalCount(), page, size, totalPages);
    }
}
