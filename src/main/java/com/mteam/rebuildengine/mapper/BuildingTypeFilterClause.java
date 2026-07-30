package com.mteam.rebuildengine.mapper;

import com.mteam.rebuildengine.utils.PropertyTypeAreaFilter;
import com.mteam.rebuildengine.utils.PropertyTypeClassifier;

import java.math.BigDecimal;
import java.util.List;

// F-04 §2.1-a propertyTypeFilters 한 항목을 SQL 조건으로 조립하기 위한 형태.
// 아파트/연립다세대는 세대당 추정면적(gfa/hh_cnt), 단독다가구/상업업무용/공장창고는 건물 전체면적(gfa) 기준 —
// PropertyTypeClassifier.usageNamesFor/APARTMENT_MIN_FLOORS와 동일한 분류 기준을 SQL에서 재사용한다.
public record BuildingTypeFilterClause(
        String kind,
        List<String> usageNames,
        boolean apartment,
        int apartmentMinFloors,
        BigDecimal areaMin,
        BigDecimal areaMax
) {
    public static final String KIND_HOUSEHOLD_AREA = "HOUSEHOLD_AREA";
    public static final String KIND_USAGE_NAME_AREA = "USAGE_NAME_AREA";
    public static final String KIND_ALWAYS_FALSE = "ALWAYS_FALSE";

    public static BuildingTypeFilterClause from(PropertyTypeAreaFilter filter) {
        return switch (filter.type()) {
            case APARTMENT -> new BuildingTypeFilterClause(KIND_HOUSEHOLD_AREA,
                    List.of(PropertyTypeClassifier.MULTI_FAMILY_USAGE_NAME), true,
                    PropertyTypeClassifier.APARTMENT_MIN_FLOORS, filter.areaMin(), filter.areaMax());
            case ROW_HOUSE -> new BuildingTypeFilterClause(KIND_HOUSEHOLD_AREA,
                    List.of(PropertyTypeClassifier.MULTI_FAMILY_USAGE_NAME), false,
                    PropertyTypeClassifier.APARTMENT_MIN_FLOORS, filter.areaMin(), filter.areaMax());
            case OFFICETEL -> new BuildingTypeFilterClause(KIND_ALWAYS_FALSE, List.of(), false, 0, null, null);
            case SINGLE_FAMILY, COMMERCIAL, INDUSTRIAL -> new BuildingTypeFilterClause(KIND_USAGE_NAME_AREA,
                    List.copyOf(PropertyTypeClassifier.usageNamesFor(filter.type())), false, 0,
                    filter.areaMin(), filter.areaMax());
        };
    }
}
