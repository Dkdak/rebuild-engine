package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.request.PropertySearchRequest;
import com.mteam.rebuildengine.model.response.BuildingInfoResponse;
import com.mteam.rebuildengine.model.response.BuildingTitleListResponse;
import com.mteam.rebuildengine.model.response.GradeSummaryResponse;
import com.mteam.rebuildengine.model.response.PropertyResponse;
import com.mteam.rebuildengine.model.response.PropertySearchResponse;
import com.mteam.rebuildengine.utils.PropertyType;
import com.mteam.rebuildengine.utils.PropertyTypeAreaFilter;
import com.mteam.rebuildengine.utils.PropertyTypeClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PropertyServiceImpl implements PropertyService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 5;

    private final BuildingService buildingService;

    @Override
    public PropertySearchResponse search(PropertySearchRequest request) {
        boolean hasBjdongCd = StringUtils.hasText(request.bjdongCd());
        boolean hasBuildingId = StringUtils.hasText(request.buildingId());
        if (hasBjdongCd && hasBuildingId) {
            throw new IllegalArgumentException("bjdongCd와 buildingId를 동시에 전달할 수 없습니다.");
        }
        validateGrade(request.grade());

        List<PropertyTypeAreaFilter> propertyTypeFilters = resolvePropertyTypeFilters(request.propertyTypeFilters());
        int page = request.page() != null ? request.page() : DEFAULT_PAGE;
        int size = request.size() != null ? request.size() : DEFAULT_SIZE;

        // 1차엔 F-09 등급 산정이 없어 모든 매물의 grade가 null — grade가 지정되면 항상 0건(§2.1-g,
        // 없는 데이터를 근사하지 않고 정직하게 처리, 오피스텔·§0-D와 동일 원칙).
        if (StringUtils.hasText(request.grade())) {
            return PropertySearchResponse.empty(page, size);
        }

        if (hasBuildingId) {
            return searchByBuildingId(request.buildingId(), request.buildYearMin(), request.buildYearMax(), propertyTypeFilters);
        }
        BuildingTitleListResponse buildings = buildingService.searchForPropertySearch(
                hasBjdongCd ? request.bjdongCd() : null, request.buildYearMin(), request.buildYearMax(),
                propertyTypeFilters, size, page);
        return PropertySearchResponse.of(buildings, page, size);
    }

    // 문자열 type을 PropertyType으로 변환·검증(§3.2 잘못된 값 → 400), area 범위 역전도 방어(§2.4).
    private static List<PropertyTypeAreaFilter> resolvePropertyTypeFilters(List<PropertySearchRequest.PropertyTypeFilter> filters) {
        if (filters == null) {
            return List.of();
        }
        return filters.stream().map(PropertyServiceImpl::toPropertyTypeAreaFilter).toList();
    }

    private static void validateGrade(String grade) {
        if (StringUtils.hasText(grade) && !GradeSummaryResponse.GRADES.contains(grade)) {
            throw new IllegalArgumentException("알 수 없는 grade 값입니다: " + grade);
        }
    }

    private static PropertyTypeAreaFilter toPropertyTypeAreaFilter(PropertySearchRequest.PropertyTypeFilter filter) {
        PropertyType type = PropertyType.fromLabel(filter.type())
                .orElseThrow(() -> new IllegalArgumentException("propertyTypeFilters에 알 수 없는 유형이 있습니다: " + filter.type()));
        if (filter.areaMin() != null && filter.areaMax() != null && filter.areaMin().compareTo(filter.areaMax()) > 0) {
            throw new IllegalArgumentException(type.label() + " 면적 필터의 areaMin이 areaMax보다 큽니다.");
        }
        return new PropertyTypeAreaFilter(type, filter.areaMin(), filter.areaMax());
    }

    // 통합 검색으로 특정 건물 하나를 이미 선택한 상태라 필터는 그 건물이 조건에 맞는지 거르는 용도로만
    // 쓰인다 — 안 맞으면 빈 결과.
    private PropertySearchResponse searchByBuildingId(String buildingId, Integer buildYearMin, Integer buildYearMax,
                                                        List<PropertyTypeAreaFilter> propertyTypeFilters) {
        List<PropertyResponse> items = buildingService.findByBdrgSn(buildingId)
                .filter(building -> matchesBuildYear(building, buildYearMin, buildYearMax))
                .filter(building -> matchesPropertyTypeFilters(building, propertyTypeFilters))
                .map(PropertyResponse::from)
                .map(List::of)
                .orElseGet(List::of);
        int totalPages = items.isEmpty() ? 0 : 1;
        return new PropertySearchResponse(items, GradeSummaryResponse.emptySummary(), items.size(), 1, 1, totalPages);
    }

    private static boolean matchesBuildYear(BuildingInfoResponse building, Integer buildYearMin, Integer buildYearMax) {
        Integer buildYear = building.useApprovalDate() != null ? building.useApprovalDate().getYear() : null;
        if (buildYearMin != null && (buildYear == null || buildYear < buildYearMin)) {
            return false;
        }
        return buildYearMax == null || (buildYear != null && buildYear <= buildYearMax);
    }

    private static boolean matchesPropertyTypeFilters(BuildingInfoResponse building, List<PropertyTypeAreaFilter> filters) {
        if (filters.isEmpty()) {
            return true;
        }
        return PropertyTypeClassifier.classify(building.mainUsageNm(), building.groundFloors())
                .filter(type -> filters.stream().anyMatch(filter -> matchesFilter(building, type, filter)))
                .isPresent();
    }

    private static boolean matchesFilter(BuildingInfoResponse building, PropertyType type, PropertyTypeAreaFilter filter) {
        if (filter.type() != type) {
            return false;
        }
        if (filter.areaMin() == null && filter.areaMax() == null) {
            return true;
        }
        BigDecimal area = areaForFilterComparison(building, type);
        if (area == null) {
            return false;
        }
        if (filter.areaMin() != null && area.compareTo(filter.areaMin()) < 0) {
            return false;
        }
        return filter.areaMax() == null || area.compareTo(filter.areaMax()) <= 0;
    }

    // 아파트/연립다세대는 세대당 추정 면적(gfa/hh_cnt) 기준(§2.1-a) — hh_cnt 없거나 0이면 계산 불가로
    // null(면적 조건이 있을 때만 이 메서드가 호출되므로 null이면 매칭 실패 처리, BuildingRepositoryImpl과 동일 기준).
    private static BigDecimal areaForFilterComparison(BuildingInfoResponse building, PropertyType type) {
        if (type != PropertyType.APARTMENT && type != PropertyType.ROW_HOUSE) {
            return building.grossFloorArea();
        }
        Integer householdCount = building.householdCount();
        if (householdCount == null || householdCount == 0 || building.grossFloorArea() == null) {
            return null;
        }
        return building.grossFloorArea().divide(BigDecimal.valueOf(householdCount), 4, RoundingMode.HALF_UP);
    }
}
