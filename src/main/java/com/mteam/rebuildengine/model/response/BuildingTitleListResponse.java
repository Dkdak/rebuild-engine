package com.mteam.rebuildengine.model.response;

import java.util.List;

// FEATURE_05 §3.1 GET /api/v1/buildings/title 응답 스키마.
public record BuildingTitleListResponse(long totalCount, List<BuildingInfoResponse> items) {
    public static BuildingTitleListResponse of(long totalCount, List<BuildingInfoResponse> items) {
        return new BuildingTitleListResponse(totalCount, items);
    }
}
