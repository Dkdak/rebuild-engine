package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;

// F-08 §3.4-B/§3.6 "추정 시세" — value는 confidenceLevel==UNAVAILABLE이면 null.
public record EstimatedPriceResponse(BigDecimal value, ConfidenceLevel confidenceLevel, long comparableCount) {

    public static EstimatedPriceResponse unavailable() {
        return new EstimatedPriceResponse(null, ConfidenceLevel.UNAVAILABLE, 0);
    }
}
