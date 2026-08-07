package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;

// F-07 §2.1 "산출 근거 목록" — status가 AVAILABLE일 때만 전부 채워짐, 그 외엔 null.
// agingFactorDefault(2026-08-08 추가, F-10 §2.4): aging_factor.default_factor 그대로 노출 —
// min/max 범위 계산엔 안 쓰이는 값이라 원래 DTO에 없었는데, 참고용으로 같이 보여달라는 요청으로 추가.
public record CostBasisResponse(
        BigDecimal grossFloorArea,
        String structureNm,
        String propertyType,
        BigDecimal baseUnitPricePerSqm,
        Integer buildingAgeYears,
        BigDecimal agingFactorMin,
        BigDecimal agingFactorMax,
        BigDecimal agingFactorDefault
) {
}
