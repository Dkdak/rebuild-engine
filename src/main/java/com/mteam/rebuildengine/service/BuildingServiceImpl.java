package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.model.entity.BuildingGisMappingEntity;
import com.mteam.rebuildengine.model.entity.GisBuildingEntity;
import com.mteam.rebuildengine.model.response.BuildingInfoResponse;
import com.mteam.rebuildengine.model.response.BuildingTitleListResponse;
import com.mteam.rebuildengine.repository.BuildingGisMappingRepository;
import com.mteam.rebuildengine.repository.BuildingRepository;
import com.mteam.rebuildengine.repository.BuildingSearchCriteria;
import com.mteam.rebuildengine.repository.GisBuildingRepository;
import com.mteam.rebuildengine.repository.LegalDongCodeRepository;
import com.mteam.rebuildengine.utils.PropertyTypeAreaFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildingServiceImpl implements BuildingService {

    // F-04 §0-C "위치 미지정 시 서울시청이 속한 중구로 범위 제한" 기본값.
    private static final String DEFAULT_SGG_NM = "서울특별시 중구";

    // F-04 §0-C 건축연도 필터 기본값.
    private static final int DEFAULT_MIN_BUILDING_AGE_YEARS = 20;

    private final BuildingRepository buildingRepository;
    private final LegalDongCodeRepository legalDongCodeRepository;
    private final BuildingGisMappingRepository buildingGisMappingRepository;
    private final GisBuildingRepository gisBuildingRepository;

    @Override
    public BuildingTitleListResponse searchTitle(String sigunguCd, String bjdongCd, String platGbCd,
                                                   String bun, String ji, int numOfRows, int pageNo) {
        // 법정동코드(legal_dong_code.bjdong_cd)는 10자리 전체 코드다(F-14 §3.2 PNU 규칙과 동일).
        // 국토교통부 API 관례를 따라 sigunguCd(5자리)+bjdongCd(5자리)로 나눠 받아 이어붙인다.
        return legalDongCodeRepository.findById(sigunguCd + bjdongCd)
                .map(dong -> search(dong.getSggNm(), dong.getBjdongNm(), platGbCd, bun, ji, numOfRows, pageNo))
                .orElseGet(() -> BuildingTitleListResponse.of(0, List.of()));
    }

    @Override
    public BuildingTitleListResponse searchForPropertySearch(String bjdongCd, Integer buildYearMin, Integer buildYearMax,
                                                               List<PropertyTypeAreaFilter> propertyTypeFilters,
                                                               int numOfRows, int pageNo) {
        LocalDate useApprovalDateMin = buildYearMin != null ? LocalDate.of(buildYearMin, 1, 1) : null;
        LocalDate useApprovalDateMax = resolveUseApprovalDateMax(buildYearMin, buildYearMax);

        if (bjdongCd == null) {
            return searchByFilters(DEFAULT_SGG_NM, null, useApprovalDateMin, useApprovalDateMax,
                    propertyTypeFilters, numOfRows, pageNo);
        }
        return legalDongCodeRepository.findById(bjdongCd)
                .map(dong -> searchByFilters(dong.getSggNm(), dong.getBjdongNm(), useApprovalDateMin, useApprovalDateMax,
                        propertyTypeFilters, numOfRows, pageNo))
                .orElseGet(() -> BuildingTitleListResponse.of(0, List.of()));
    }

    // buildYearMin/Max 둘 다 미지정이면 20년 이상 경과 기본값(§0-C) 적용, 하나라도 지정되면 사용자
    // 값을 그대로 따른다(기본값 미적용).
    private static LocalDate resolveUseApprovalDateMax(Integer buildYearMin, Integer buildYearMax) {
        if (buildYearMax != null) {
            return LocalDate.of(buildYearMax, 12, 31);
        }
        if (buildYearMin != null) {
            return null;
        }
        return LocalDate.now().minusYears(DEFAULT_MIN_BUILDING_AGE_YEARS);
    }

    private BuildingTitleListResponse searchByFilters(String sggNm, String bjdongNm,
                                                        LocalDate useApprovalDateMin, LocalDate useApprovalDateMax,
                                                        List<PropertyTypeAreaFilter> propertyTypeFilters,
                                                        int numOfRows, int pageNo) {
        BuildingSearchCriteria criteria = new BuildingSearchCriteria(sggNm, bjdongNm,
                useApprovalDateMin, useApprovalDateMax, propertyTypeFilters);
        Page<BuildingEntity> page = buildingRepository.search(criteria, PageRequest.of(pageNo - 1, numOfRows));

        Map<String, GisBuildingEntity> gisBuildingsByBdrgSn = loadGisBuildingsByBdrgSn(page.getContent());
        List<BuildingInfoResponse> items = page.getContent().stream()
                .map(building -> toResponse(building, gisBuildingsByBdrgSn.get(building.getBdrgSn())))
                .toList();

        return BuildingTitleListResponse.of(page.getTotalElements(), items);
    }

    @Override
    public Optional<BuildingInfoResponse> findByBdrgSn(String bdrgSn) {
        return buildingRepository.findById(bdrgSn)
                .filter(building -> !building.isDeleted())
                .map(building -> toResponse(building, loadGisBuildingsByBdrgSn(List.of(building)).get(bdrgSn)));
    }

    private BuildingTitleListResponse search(String sggNm, String bjdongNm, String platGbCd,
                                               String bun, String ji, int numOfRows, int pageNo) {
        Page<BuildingEntity> page = buildingRepository.searchByDong(
                sggNm, bjdongNm, platGbCd, bun, ji, PageRequest.of(pageNo - 1, numOfRows));

        Map<String, GisBuildingEntity> gisBuildingsByBdrgSn = loadGisBuildingsByBdrgSn(page.getContent());
        List<BuildingInfoResponse> items = page.getContent().stream()
                .map(building -> toResponse(building, gisBuildingsByBdrgSn.get(building.getBdrgSn())))
                .toList();

        return BuildingTitleListResponse.of(page.getTotalElements(), items);
    }

    private static BuildingInfoResponse toResponse(BuildingEntity building, GisBuildingEntity gis) {
        BigDecimal lat = gis != null ? gis.getCentroidLat() : null;
        BigDecimal lng = gis != null ? gis.getCentroidLng() : null;
        return BuildingInfoResponse.of(building, lat, lng);
    }

    // building_gis_mapping을 거쳐 gis_building의 좌표를 얻는다 — 배치로 미리 계산된 매핑을 읽기만 한다
    // (F-12 §3.5 "조회 원칙", 온디맨드 지오코딩 아님).
    private Map<String, GisBuildingEntity> loadGisBuildingsByBdrgSn(List<BuildingEntity> buildings) {
        List<String> bdrgSns = buildings.stream().map(BuildingEntity::getBdrgSn).toList();
        List<BuildingGisMappingEntity> mappings = buildingGisMappingRepository.findByBuildingIdIn(bdrgSns);

        List<Long> gisBuildingIds = mappings.stream()
                .map(BuildingGisMappingEntity::getGisBuildingId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, GisBuildingEntity> gisBuildingsById = gisBuildingRepository.findAllById(gisBuildingIds).stream()
                .collect(Collectors.toMap(GisBuildingEntity::getId, Function.identity()));

        return mappings.stream()
                .filter(mapping -> gisBuildingsById.containsKey(mapping.getGisBuildingId()))
                .collect(Collectors.toMap(BuildingGisMappingEntity::getBuildingId,
                        mapping -> gisBuildingsById.get(mapping.getGisBuildingId())));
    }
}
