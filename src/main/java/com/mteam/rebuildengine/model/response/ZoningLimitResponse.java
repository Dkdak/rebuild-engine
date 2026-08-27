package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;

// FEATURE_19_PERSONALIZED_ANALYSIS.md §2.2-e — STEP1 용도지역 드롭다운용, LAW-002(zoning_limit)
// 16종 전체 + 상한. 프론트가 용도지역을 고르면 저장 전에도 이 목록에서 floorAreaRatioLimit을 그대로
// 표시할 수 있다(계산 없음) — zoning_limit이 유일한 출처라 값을 프론트 코드에 복사하지 않는다.
public record ZoningLimitResponse(String zoneName, BigDecimal floorAreaRatioLimit, BigDecimal coverageRatioLimit) {
}
