package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;

// F-07 §2.1 "산출 근거 목록" — status가 AVAILABLE일 때만 전부 채워짐, 그 외엔 null.
public record CostBasisResponse(
        BigDecimal grossFloorArea,
        String structureNm,
        String propertyType,
        BigDecimal baseUnitPricePerSqm,
        Integer buildingAgeYears,
        BigDecimal agingFactorMin,
        BigDecimal agingFactorMax
) {
}
