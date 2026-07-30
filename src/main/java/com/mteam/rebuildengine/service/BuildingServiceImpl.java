package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.mapper.BuildingDongSearchCondition;
import com.mteam.rebuildengine.mapper.BuildingMapper;
import com.mteam.rebuildengine.mapper.BuildingPropertySearchCondition;
import com.mteam.rebuildengine.mapper.BuildingTypeFilterClause;
import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.model.entity.BuildingGisMappingEntity;
import com.mteam.rebuildengine.model.entity.GisBuildingEntity;
import com.mteam.rebuildengine.model.read.BuildingReadModel;
import com.mteam.rebuildengine.model.read.GradeSummaryReadModel;
import com.mteam.rebuildengine.model.response.BuildingInfoResponse;
import com.mteam.rebuildengine.model.response.BuildingTitleListResponse;
import com.mteam.rebuildengine.repository.BuildingGisMappingRepository;
import com.mteam.rebuildengine.repository.BuildingRepository;
import com.mteam.rebuildengine.repository.GisBuildingRepository;
import com.mteam.rebuildengine.repository.LegalDongCodeRepository;
import com.mteam.rebuildengine.utils.InvestmentGrade;
import com.mteam.rebuildengine.utils.PropertyTypeAreaFilter;
import lombok.RequiredArgsConstructor;
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

    private final BuildingRepository buildingRepository;
    private final BuildingMapper buildingMapper;
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
    public BuildingTitleListResponse searchForPropertySearch(String bjdongCd, String sigunguCd,
                                                               Integer buildYearMin, Integer buildYearMax,
                                                               List<PropertyTypeAreaFilter> propertyTypeFilters,
                                                               InvestmentGrade grade, int numOfRows, int pageNo) {
        LocalDate useApprovalDateMin = buildYearMin != null ? LocalDate.of(buildYearMin, 1, 1) : null;
        LocalDate useApprovalDateMax = buildYearMax != null ? LocalDate.of(buildYearMax, 12, 31) : null;

        return resolveLocationScope(bjdongCd, sigunguCd)
                .map(location -> searchByFilters(location.sggNm(), location.bjdongNm(), useApprovalDateMin, useApprovalDateMax,
                        propertyTypeFilters, grade, numOfRows, pageNo))
                .orElseGet(() -> BuildingTitleListResponse.of(0, List.of()));
    }

    @Override
    public List<GradeSummaryReadModel> gradeSummaryForPropertySearch(String bjdongCd, String sigunguCd,
                                                                       Integer buildYearMin, Integer buildYearMax,
                                                                       List<PropertyTypeAreaFilter> propertyTypeFilters) {
        LocalDate useApprovalDateMin = buildYearMin != null ? LocalDate.of(buildYearMin, 1, 1) : null;
        LocalDate useApprovalDateMax = buildYearMax != null ? LocalDate.of(buildYearMax, 12, 31) : null;

        return resolveLocationScope(bjdongCd, sigunguCd)
                .map(location -> {
                    BuildingPropertySearchCondition condition = new BuildingPropertySearchCondition(
                            location.sggNm(), location.bjdongNm(), useApprovalDateMin, useApprovalDateMax,
                            toTypeFilterClauses(propertyTypeFilters), null, 0, 0);
                    return buildingMapper.gradeSummaryForPropertySearch(condition);
                })
                .orElseGet(List::of);
    }

    // bjdongCd(법정동)가 sigunguCd(구)보다 더 구체적이라 우선한다. 둘 다 없으면 위치 제한 없음(빈 값이 아닌
    // sggNm=null인 유효한 스코프, §0-C). 코드값은 있는데 legal_dong_code에 없으면 빈 Optional(호출부가
    // 빈 결과로 처리).
    private Optional<LocationScope> resolveLocationScope(String bjdongCd, String sigunguCd) {
        if (bjdongCd != null) {
            return legalDongCodeRepository.findById(bjdongCd)
                    .map(dong -> new LocationScope(dong.getSggNm(), dong.getBjdongNm()));
        }
        if (sigunguCd != null) {
            return legalDongCodeRepository.findFirstBySigunguCd(sigunguCd)
                    .map(dong -> new LocationScope(dong.getSggNm(), null));
        }
        return Optional.of(new LocationScope(null, null));
    }

    private record LocationScope(String sggNm, String bjdongNm) {
    }

    private static List<BuildingTypeFilterClause> toTypeFilterClauses(List<PropertyTypeAreaFilter> propertyTypeFilters) {
        return propertyTypeFilters == null ? List.of()
                : propertyTypeFilters.stream().map(BuildingTypeFilterClause::from).toList();
    }

    private BuildingTitleListResponse searchByFilters(String sggNm, String bjdongNm,
                                                        LocalDate useApprovalDateMin, LocalDate useApprovalDateMax,
                                                        List<PropertyTypeAreaFilter> propertyTypeFilters,
                                                        InvestmentGrade grade, int numOfRows, int pageNo) {
        BuildingPropertySearchCondition condition = new BuildingPropertySearchCondition(sggNm, bjdongNm,
                useApprovalDateMin, useApprovalDateMax, toTypeFilterClauses(propertyTypeFilters),
                grade != null ? grade.getDisplayName() : null, numOfRows, (pageNo - 1) * numOfRows);
        List<BuildingReadModel> buildings = buildingMapper.searchForPropertySearch(condition);
        long total = buildingMapper.countForPropertySearch(condition);

        List<String> bdrgSns = buildings.stream().map(BuildingReadModel::bdrgSn).toList();
        Map<String, GisBuildingEntity> gisBuildingsByBdrgSn = loadGisBuildingsByBdrgSn(bdrgSns);
        List<BuildingInfoResponse> items = buildings.stream()
                .map(building -> toResponse(building, gisBuildingsByBdrgSn.get(building.bdrgSn())))
                .toList();

        return BuildingTitleListResponse.of(total, items);
    }

    @Override
    public Optional<BuildingInfoResponse> findByBdrgSn(String bdrgSn) {
        return buildingRepository.findById(bdrgSn)
                .filter(building -> !building.isDeleted())
                .map(building -> toResponse(building, loadGisBuildingsByBdrgSn(List.of(bdrgSn)).get(bdrgSn)));
    }

    private BuildingTitleListResponse search(String sggNm, String bjdongNm, String platGbCd,
                                               String bun, String ji, int numOfRows, int pageNo) {
        BuildingDongSearchCondition condition = new BuildingDongSearchCondition(
                sggNm, bjdongNm, platGbCd, bun, ji, numOfRows, (pageNo - 1) * numOfRows);
        List<BuildingReadModel> buildings = buildingMapper.searchByDong(condition);
        long total = buildingMapper.countByDong(condition);

        List<String> bdrgSns = buildings.stream().map(BuildingReadModel::bdrgSn).toList();
        Map<String, GisBuildingEntity> gisBuildingsByBdrgSn = loadGisBuildingsByBdrgSn(bdrgSns);
        List<BuildingInfoResponse> items = buildings.stream()
                .map(building -> toResponse(building, gisBuildingsByBdrgSn.get(building.bdrgSn())))
                .toList();

        return BuildingTitleListResponse.of(total, items);
    }

    private static BuildingInfoResponse toResponse(BuildingEntity building, GisBuildingEntity gis) {
        BigDecimal lat = gis != null ? gis.getCentroidLat() : null;
        BigDecimal lng = gis != null ? gis.getCentroidLng() : null;
        return BuildingInfoResponse.of(building, lat, lng);
    }

    private static BuildingInfoResponse toResponse(BuildingReadModel building, GisBuildingEntity gis) {
        BigDecimal lat = gis != null ? gis.getCentroidLat() : null;
        BigDecimal lng = gis != null ? gis.getCentroidLng() : null;
        return BuildingInfoResponse.of(building, lat, lng);
    }

    // building_gis_mapping을 거쳐 gis_building의 좌표를 얻는다 — 배치로 미리 계산된 매핑을 읽기만 한다
    // (F-12 §3.5 "조회 원칙", 온디맨드 지오코딩 아님).
    private Map<String, GisBuildingEntity> loadGisBuildingsByBdrgSn(List<String> bdrgSns) {
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
