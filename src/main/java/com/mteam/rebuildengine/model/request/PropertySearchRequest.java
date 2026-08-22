package com.mteam.rebuildengine.model.request;

import java.math.BigDecimal;
import java.util.List;

// FEATURE_04 §3.1 POST /api/v1/properties/search 요청 body. bjdongCd(법정동, 10자리)/sigunguCd(구 전체,
// 5자리, 통합 검색 GU 후보)/buildingId 중 최대 하나만 전달 가능. 위치 기본값은 폐기됐다(§0-C, 2026-08-03) —
// 셋 다 없으면 buildYearMin/Max 중 하나는 있어야 하고, 그마저 없으면 400(PropertyServiceImpl 검증).
// propertyTypeFilters가 비어 있으면 유형/면적 제한 없음(§2.4 "전체"). 유형별로 면적 단위가 달라(§2.1-a)
// areaMin/Max를 필터마다 따로 받는다 — 배열 구조라 GET 쿼리보다 POST body가 자연스러워 전환했다(§5.1 Open
// Item 결정, 2026-07-28).
// grade(§2.1-g, 리스트 헤더 등급 배지 클릭 시 전달, 단일값) — F-09 배치(investment_result) 완료로
// 실제 값 기준 필터링(2026-08-08). "A"/"B"/"C"/"D"/"NA"(정보부족, §3.3 2026-08-09 등급체계 축소)
// 외의 값이면 400(InvestmentGrade.fromDisplayName).
// remodelingCandidate/zoneConfirmed/farSurplusPositive/districtUnrestricted(2026-08-23 추가, product
// 요청) — 대시보드가 정의한 "리모델링 후보" 4조건을 지도 검색에서도 필터로 쓸 수 있게 한다. 각각
// 독립 선택(null이면 필터 미적용)이고 AND로 결합(investment_result의 flat 컬럼 그대로 조회).
public record PropertySearchRequest(
        String bjdongCd,
        String sigunguCd,
        String buildingId,
        Integer buildYearMin,
        Integer buildYearMax,
        List<PropertyTypeFilter> propertyTypeFilters,
        String grade,
        Boolean remodelingCandidate,
        Boolean zoneConfirmed,
        Boolean farSurplusPositive,
        Boolean districtUnrestricted,
        Integer page,
        Integer size
) {
    public record PropertyTypeFilter(String type, BigDecimal areaMin, BigDecimal areaMax) {
    }
}
