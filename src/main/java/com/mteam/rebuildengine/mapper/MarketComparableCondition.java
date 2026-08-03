package com.mteam.rebuildengine.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

// F-08 §3.4-B/§3.5 유사 거래 검색 조건. bjdongNm이 null이면 구(sggNm) 전체로 검색 범위가 넓어진
// 단계(§3.5 1단계)라는 뜻 — dongCondition과 동일하게 "null이면 그 조건 생략" 관례를 따른다.
// buildYearMin/Max도 대상 건물의 준공연도를 모르면 null(연식 조건 자체를 생략).
public record MarketComparableCondition(
        String sggNm,
        String bjdongNm,
        String propertyType,
        BigDecimal areaMin,
        BigDecimal areaMax,
        Integer buildYearMin,
        Integer buildYearMax,
        LocalDate recencyCutoff
) {
}
