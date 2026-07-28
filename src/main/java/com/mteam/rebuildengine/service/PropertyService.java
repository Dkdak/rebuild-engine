package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.request.PropertySearchRequest;
import com.mteam.rebuildengine.model.response.PropertySearchResponse;

public interface PropertyService {
    // FEATURE_04 §3.1 POST /api/v1/properties/search.
    PropertySearchResponse search(PropertySearchRequest request);
}
