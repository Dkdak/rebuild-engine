package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;
import java.util.List;

// FEATURE_19_PERSONALIZED_ANALYSIS.md §1.1 — F-10 리포트 CASE1(공공데이터)/CASE2(실측 반영) 통합
// 응답. caseTwo는 로그인 계정에 이 매물의 활성 실측 데이터가 있는지 — 있으면 measured 값들이 실측
// 기준으로 바뀌고, 없으면 전부 추정(measured=false)이라 CASE1과 동일한 내용이 된다(별도 분기 없이
// 이 응답 하나로 두 CASE를 다 표현). 등급은 어느 CASE든 안 바뀐다(F-09 §5.1 재검토 대기, 공공데이터
// 기준 하나로 유지).
//
// 바뀌는 범위(§1.1 표): 01/06(총투자금·미래가치·ROI) · 05(증축 여력) · 08(분석의 한계 해소 현황) ·
// 04(시장 내 가격 위치, 2026-08-27 추가) — MarketService.getMeasuredPricePosition()으로 실측 매입가
// 기준 재계산, 측정 안 됐거나 재계산 자체가 안 되면 공공데이터 기준(measured=false)으로 폴백한다.
public record BuildingReportResponse(
        boolean caseTwo,
        String grade,
        ValuedField<BigDecimal> totalInvestment,
        ValuedField<BigDecimal> projectedValue,
        ValuedField<BigDecimal> expectedProfit,
        ValuedField<BigDecimal> roi,
        ValuedField<BigDecimal> additionalBuildableAreaSqm,
        Integer estimatedAdditionalHouseholds,
        ValuedField<PricePositionResponse> pricePosition,
        List<LimitationResolutionResponse> limitations
) {
}
