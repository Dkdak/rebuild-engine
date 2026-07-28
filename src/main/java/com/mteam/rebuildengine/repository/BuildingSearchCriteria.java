package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.utils.PropertyTypeAreaFilter;

import java.time.LocalDate;
import java.util.List;

// F-04 §3.1 properties/search 조건 묶음 — BuildingRepositoryCustom.search()가 이 하나의 조건 객체를
// 받는다(HELP6 §2, §6: Repository 메서드명에 검색 조건을 전부 표현하지 않는다).
// bjdongNm이 null이면 구 전체(위치 미지정 기본값 중구, §0-C)를 의미한다.
// propertyTypeFilters가 비어 있으면 유형/면적 제한 없음("전체", §2.4) — 비어 있지 않으면 목록의 유형 중
// 하나라도 맞고 그 유형에 지정된 면적 범위 안에 들면 포함(§2.1-a, 유형마다 면적 단위가 달라 분리).
public record BuildingSearchCriteria(
        String sggNm,
        String bjdongNm,
        LocalDate useApprovalDateMin,
        LocalDate useApprovalDateMax,
        List<PropertyTypeAreaFilter> propertyTypeFilters
) {
}
