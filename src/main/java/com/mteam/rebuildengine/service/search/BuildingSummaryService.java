package com.mteam.rebuildengine.service.search;

import com.mteam.rebuildengine.model.response.BuildingSummaryResponse;

import java.util.Optional;

public interface BuildingSummaryService {
    Optional<BuildingSummaryResponse> getBuildingSummary(String buildingId);
}
