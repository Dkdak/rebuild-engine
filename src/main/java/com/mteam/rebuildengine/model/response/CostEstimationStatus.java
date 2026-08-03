package com.mteam.rebuildengine.model.response;

// FEATURE_07_COST.md §3.3 예외 처리 3종 + 정상 산출.
public enum CostEstimationStatus {
    AVAILABLE,
    NOT_APPLICABLE_REMODELING_NOT_POSSIBLE,
    NO_REFERENCE_RATE,
    AREA_UNAVAILABLE
}
