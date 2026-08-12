package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;

// F-07 §2.1 "산출 근거 목록" — status가 AVAILABLE일 때만 전부 채워짐, 그 외엔 null.
// agingFactorMin/Max/Default 셋 다 CostServiceImpl.agingFactorValue(rn, k)로 변환된 "최종 노후도
// 보정계수"라 minCost/maxCost와 같은 방식(grossFloorArea × baseUnitPricePerSqm × factor)으로 그대로
// 곱하면 각 시나리오 비용이 나온다 — F-10 민감도 분석의 "기준" 시나리오가 이 방식으로 재계산한다(§2.4).
// source(2026-08-10 추가, F-10 프론트 요청): cost_base_price.source 그대로 노출 — 고시 번호가
// 코드 상수가 아니라 참조 테이블 값이라(§3.2), 프론트가 하드코딩하면 고시 개정 시 화면이 안 맞게 된다.
public record CostBasisResponse(
        BigDecimal grossFloorArea,
        String structureNm,
        String propertyType,
        BigDecimal baseUnitPricePerSqm,
        Integer buildingAgeYears,
        BigDecimal agingFactorMin,
        BigDecimal agingFactorMax,
        BigDecimal agingFactorDefault,
        String source
) {
}
