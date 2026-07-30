package com.mteam.rebuildengine.model.response;

import java.util.List;
import java.util.function.Function;

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
    // converter는 investment_result(grade/roi) 배치 조회 결과를 building별로 붙여 PropertyResponse를
    // 만든다 — Entity가 이 response 계층으로 새어 들어오지 않도록 Service가 값(String/BigDecimal)만
    // 넘기는 변환 함수를 만들어 전달한다(HELP2 §6.1 Entity 직접 노출 금지).
    public static PropertySearchResponse of(BuildingTitleListResponse buildings, List<GradeSummaryResponse> gradeSummary,
                                             Function<BuildingInfoResponse, PropertyResponse> converter,
                                             int page, int size) {
        List<PropertyResponse> items = buildings.items().stream().map(converter).toList();
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) buildings.totalCount() / size);
        return new PropertySearchResponse(items, gradeSummary, buildings.totalCount(), page, size, totalPages);
    }
}
