package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.response.BuildingInfoResponse;
import com.mteam.rebuildengine.model.response.BuildingTitleListResponse;
import com.mteam.rebuildengine.utils.PropertyTypeAreaFilter;

import java.util.List;
import java.util.Optional;

public interface BuildingService {
    // FEATURE_05 §3.1 GET /api/v1/buildings/title. bun/ji 생략 시 해당 법정동 전체 목록.
    BuildingTitleListResponse searchTitle(String sigunguCd, String bjdongCd, String platGbCd,
                                           String bun, String ji, int numOfRows, int pageNo);

    // FEATURE_04 §3.1 properties/search — bjdongCd(legal_dong_code.bjdong_cd 10자리 전체, null이면
    // 위치 미지정 기본값인 중구 전체, §0-C)와 buildYear/propertyTypeFilters(유형별 면적, §2.1-a) 필터를
    // 함께 적용한다. searchTitle의 sigunguCd+bjdongCd(5자리씩 분리) 조회와는 별개 경로.
    BuildingTitleListResponse searchForPropertySearch(String bjdongCd, Integer buildYearMin, Integer buildYearMax,
                                                        List<PropertyTypeAreaFilter> propertyTypeFilters,
                                                        int numOfRows, int pageNo);

    // FEATURE_04 §1.2 통합 검색 — BUILDING 후보 선택 시 단건 조회.
    Optional<BuildingInfoResponse> findByBdrgSn(String bdrgSn);
}
