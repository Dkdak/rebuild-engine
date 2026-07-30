package com.mteam.rebuildengine.model.read;

import java.math.BigDecimal;

public record SearchIndexReadModel(
        String type,
        String buildingId,
        String bjdongCd,
        String displayText,
        BigDecimal lat,
        BigDecimal lng
) {
}
