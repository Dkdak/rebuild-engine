package com.mteam.rebuildengine.model.response;

import com.mteam.rebuildengine.model.entity.SearchIndexEntity;

import java.math.BigDecimal;

// FEATURE_04 §3.1 GET /api/v1/search-index/search 응답 항목. BUILDING만 buildingId/lat/lng 값이 있다.
public record SearchIndexCandidateResponse(
        String type,
        String buildingId,
        String bjdongCd,
        String displayText,
        BigDecimal lat,
        BigDecimal lng
) {
    public static SearchIndexCandidateResponse from(SearchIndexEntity entity) {
        return new SearchIndexCandidateResponse(
                entity.getType(),
                entity.getBuildingId(),
                entity.getBjdongCd(),
                entity.getDisplayText(),
                entity.getLat(),
                entity.getLng()
        );
    }
}
