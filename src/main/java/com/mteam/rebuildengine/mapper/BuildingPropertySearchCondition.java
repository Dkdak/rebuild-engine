package com.mteam.rebuildengine.mapper;

import java.time.LocalDate;
import java.util.List;

// F-04 §3.1 properties/search 조건 묶음. bjdongNm이 null이면 구 전체(§0-C 위치 미지정 기본값 중구).
// typeFilters가 비어 있으면 유형/면적 제한 없음("전체", §2.4). grade가 null이면 등급 제한 없음
// (investment_result.grade 컬럼값 그대로, F-09 스파이크 테스트, §2.1-g).
public record BuildingPropertySearchCondition(
        String sggNm,
        String bjdongNm,
        LocalDate useApprovalDateMin,
        LocalDate useApprovalDateMax,
        List<BuildingTypeFilterClause> typeFilters,
        String grade,
        Boolean remodelingCandidate,
        Boolean zoneConfirmed,
        Boolean farSurplusPositive,
        Boolean districtUnrestricted,
        int limit,
        int offset
) {
}
