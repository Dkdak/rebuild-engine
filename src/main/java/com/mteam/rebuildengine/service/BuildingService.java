package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.read.GradeSummaryReadModel;
import com.mteam.rebuildengine.model.response.BuildingInfoResponse;
import com.mteam.rebuildengine.model.response.BuildingTitleListResponse;
import com.mteam.rebuildengine.utils.InvestmentGrade;
import com.mteam.rebuildengine.utils.PropertyTypeAreaFilter;

import java.util.List;
import java.util.Optional;

public interface BuildingService {
    // FEATURE_05 §3.1 GET /api/v1/buildings/title. bun/ji 생략 시 해당 법정동 전체 목록.
    BuildingTitleListResponse searchTitle(String sigunguCd, String bjdongCd, String platGbCd,
                                           String bun, String ji, int numOfRows, int pageNo);

    // FEATURE_04 §3.1 properties/search — bjdongCd(legal_dong_code.bjdong_cd 10자리, 법정동 범위) 또는
    // sigunguCd(legal_dong_code.sigungu_cd 5자리, 구 전체 범위, 통합 검색 GU 후보) 중 하나로 위치를 좁히고
    // buildYear/propertyTypeFilters(유형별 면적, §2.1-a)/grade(§2.1-g, F-09 스파이크 테스트) 필터를 함께
    // 적용한다. 위치 기본값은 폐기됐다(§0-C, 2026-08-03) — bjdongCd/sigunguCd 둘 다 null이면 위치 제한 없이
    // buildYear 조건만 적용(호출부에서 buildYear도 없으면 거부, PropertyServiceImpl 검증). searchTitle의
    // sigunguCd+bjdongCd(5자리씩 분리) 조회와는 별개 경로.
    BuildingTitleListResponse searchForPropertySearch(String bjdongCd, String sigunguCd,
                                                        Integer buildYearMin, Integer buildYearMax,
                                                        List<PropertyTypeAreaFilter> propertyTypeFilters,
                                                        InvestmentGrade grade, int numOfRows, int pageNo);

    // FEATURE_04 §3.1 gradeSummary — searchForPropertySearch와 같은 위치/건축연도/유형 필터를 쓰되 grade는
    // 받지 않는다(집계 대상 자체라 필터링하지 않음, PropertyServiceImpl이 A+~D 0건 채움까지 마무리).
    List<GradeSummaryReadModel> gradeSummaryForPropertySearch(String bjdongCd, String sigunguCd,
                                                                Integer buildYearMin, Integer buildYearMax,
                                                                List<PropertyTypeAreaFilter> propertyTypeFilters);

    // FEATURE_04 §1.2 통합 검색 — BUILDING 후보 선택 시 단건 조회.
    Optional<BuildingInfoResponse> findByBdrgSn(String bdrgSn);
}
