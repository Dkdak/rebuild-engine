package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.entity.InvestmentResultEntity;
import com.mteam.rebuildengine.model.read.GradeSummaryReadModel;
import com.mteam.rebuildengine.model.request.PropertySearchRequest;
import com.mteam.rebuildengine.model.response.BuildingInfoResponse;
import com.mteam.rebuildengine.model.response.BuildingTitleListResponse;
import com.mteam.rebuildengine.model.response.GradeSummaryResponse;
import com.mteam.rebuildengine.model.response.PropertyResponse;
import com.mteam.rebuildengine.model.response.PropertySearchResponse;
import com.mteam.rebuildengine.repository.InvestmentResultRepository;
import com.mteam.rebuildengine.utils.InvestmentGrade;
import com.mteam.rebuildengine.utils.PropertyType;
import com.mteam.rebuildengine.utils.PropertyTypeAreaFilter;
import com.mteam.rebuildengine.utils.PropertyTypeClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PropertyServiceImpl implements PropertyService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 5;

    private final BuildingService buildingService;
    private final InvestmentResultRepository investmentResultRepository;

    @Override
    public PropertySearchResponse search(PropertySearchRequest request) {
        boolean hasBjdongCd = StringUtils.hasText(request.bjdongCd());
        boolean hasSigunguCd = StringUtils.hasText(request.sigunguCd());
        boolean hasBuildingId = StringUtils.hasText(request.buildingId());
        long locationModeCount = Stream.of(hasBjdongCd, hasSigunguCd, hasBuildingId).filter(Boolean::booleanValue).count();
        if (locationModeCount > 1) {
            throw new IllegalArgumentException("bjdongCd, sigunguCd, buildingId는 동시에 전달할 수 없습니다.");
        }
        boolean hasBuildYear = request.buildYearMin() != null || request.buildYearMax() != null;
        if (locationModeCount == 0 && !hasBuildYear) {
            throw new IllegalArgumentException("bjdongCd, sigunguCd, buildYearMin/buildYearMax 중 하나는 입력해야 합니다.");
        }
        InvestmentGrade grade = resolveGrade(request.grade());
        List<PropertyTypeAreaFilter> propertyTypeFilters = resolvePropertyTypeFilters(request.propertyTypeFilters());
        int page = request.page() != null ? request.page() : DEFAULT_PAGE;
        int size = request.size() != null ? request.size() : DEFAULT_SIZE;

        if (hasBuildingId) {
            return searchByBuildingId(request.buildingId(), request.buildYearMin(), request.buildYearMax(),
                    propertyTypeFilters, grade);
        }
        String bjdongCd = hasBjdongCd ? request.bjdongCd() : null;
        String sigunguCd = hasSigunguCd ? request.sigunguCd() : null;
        BuildingTitleListResponse buildings = buildingService.searchForPropertySearch(
                bjdongCd, sigunguCd, request.buildYearMin(), request.buildYearMax(),
                propertyTypeFilters, grade, size, page);
        // gradeSummary는 grade 필터를 뺀 나머지 조건(위치/건축연도/유형)만 같은 스코프로 다시 집계한다 —
        // 그래야 등급 배지가 "지금 grade로 좁히면 다른 등급은 몇 건인지"를 보여줄 수 있다(§2.1-g).
        List<GradeSummaryResponse> gradeSummary = GradeSummaryResponse.from(buildingService.gradeSummaryForPropertySearch(
                bjdongCd, sigunguCd, request.buildYearMin(), request.buildYearMax(), propertyTypeFilters));
        Map<String, InvestmentResultEntity> investmentResults = loadInvestmentResults(buildings);
        return PropertySearchResponse.of(buildings, gradeSummary,
                building -> toPropertyResponse(building, investmentResults), page, size);
    }

    // §2.1-g 등급 배지 클릭 시 전달, 단일값. F-09 정식 기획 전이라 investment_result.grade에 없는
    // 값이면 400(§3.2), 값 자체는 스파이크 테스트 더미데이터라 매칭되는 건물만 정상적으로 걸러진다.
    private static InvestmentGrade resolveGrade(String grade) {
        if (!StringUtils.hasText(grade)) {
            return null;
        }
        return InvestmentGrade.fromDisplayName(grade)
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 grade 값입니다: " + grade));
    }

    private Map<String, InvestmentResultEntity> loadInvestmentResults(BuildingTitleListResponse buildings) {
        List<String> bdrgSns = buildings.items().stream().map(BuildingInfoResponse::bdrgSn).toList();
        return investmentResultRepository.findByBuildingIdInAndIsDeletedFalse(bdrgSns).stream()
                .collect(Collectors.toMap(InvestmentResultEntity::getBuildingId, Function.identity()));
    }

    private static PropertyResponse toPropertyResponse(BuildingInfoResponse building,
                                                         Map<String, InvestmentResultEntity> investmentResults) {
        InvestmentResultEntity result = investmentResults.get(building.bdrgSn());
        String grade = result != null ? result.getGrade().getDisplayName() : null;
        BigDecimal roi = result != null ? result.getRoi() : null;
        return PropertyResponse.from(building, grade, roi);
    }

    // 문자열 type을 PropertyType으로 변환·검증(§3.2 잘못된 값 → 400), area 범위 역전도 방어(§2.4).
    private static List<PropertyTypeAreaFilter> resolvePropertyTypeFilters(List<PropertySearchRequest.PropertyTypeFilter> filters) {
        if (filters == null) {
            return List.of();
        }
        return filters.stream().map(PropertyServiceImpl::toPropertyTypeAreaFilter).toList();
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
                                                        List<PropertyTypeAreaFilter> propertyTypeFilters, InvestmentGrade grade) {
        InvestmentResultEntity investmentResult = investmentResultRepository.findById(buildingId)
                .filter(result -> !result.isDeleted())
                .orElse(null);

        List<PropertyResponse> items = buildingService.findByBdrgSn(buildingId)
                .filter(building -> matchesBuildYear(building, buildYearMin, buildYearMax))
                .filter(building -> matchesPropertyTypeFilters(building, propertyTypeFilters))
                .filter(building -> grade == null || (investmentResult != null && investmentResult.getGrade() == grade))
                .map(building -> PropertyResponse.from(building,
                        investmentResult != null ? investmentResult.getGrade().getDisplayName() : null,
                        investmentResult != null ? investmentResult.getRoi() : null))
                .map(List::of)
                .orElseGet(List::of);
        int totalPages = items.isEmpty() ? 0 : 1;
        List<GradeSummaryResponse> gradeSummary = items.isEmpty() || investmentResult == null
                ? GradeSummaryResponse.emptySummary()
                : GradeSummaryResponse.from(List.of(new GradeSummaryReadModel(
                        investmentResult.getGrade().getDisplayName(), 1, investmentResult.getRoi())));
        return new PropertySearchResponse(items, gradeSummary, items.size(), 1, 1, totalPages);
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
        return PropertyTypeClassifier.estimatedUnitArea(type, building.grossFloorArea(), building.householdCount())
                .orElse(null);
    }
}
