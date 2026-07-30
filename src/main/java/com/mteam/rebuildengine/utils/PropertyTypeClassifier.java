package com.mteam.rebuildengine.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// DOMAIN.md §1 부동산유형 6종을 건축물대장 주용도명(mn_usg_cd_nm)+지상층수로 분류.
// 아파트/연립다세대는 둘 다 주용도명이 '공동주택'이라 주택법 시행령 기준(5층 이상=아파트)으로 나눈다.
// 오피스텔은 건축물대장에 구분 필드 자체가 없어 매핑하지 않는다(usageNamesFor에서 빈 Set).
// 표시용 단건 분류(classify)와 검색 조건 조립(BuildingRepositoryImpl)이 반드시 같은 기준을 쓴다.
public final class PropertyTypeClassifier {

    public static final int APARTMENT_MIN_FLOORS = 5;
    public static final String MULTI_FAMILY_USAGE_NAME = "공동주택";

    private static final Map<PropertyType, Set<String>> USAGE_NAMES_BY_TYPE = Map.of(
            PropertyType.SINGLE_FAMILY, Set.of("단독주택"),
            PropertyType.COMMERCIAL, Set.of("제1종근린생활시설", "제2종근린생활시설", "근린생활시설", "업무시설", "판매시설", "숙박시설"),
            PropertyType.INDUSTRIAL, Set.of("공장", "창고시설")
    );

    private PropertyTypeClassifier() {
    }

    // 표시용 — 건물 한 건의 주용도명/지상층수로 PropertyType 하나(매핑 불가 시 빈 값)를 반환.
    public static Optional<PropertyType> classify(String mainUsageNm, Integer groundFloors) {
        if (mainUsageNm == null) {
            return Optional.empty();
        }
        if (MULTI_FAMILY_USAGE_NAME.equals(mainUsageNm)) {
            if (groundFloors == null) {
                return Optional.empty();
            }
            return Optional.of(groundFloors >= APARTMENT_MIN_FLOORS ? PropertyType.APARTMENT : PropertyType.ROW_HOUSE);
        }
        return USAGE_NAMES_BY_TYPE.entrySet().stream()
                .filter(entry -> entry.getValue().contains(mainUsageNm))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    // 검색 조건 조립용 — APARTMENT/ROW_HOUSE(지상층수 기준)·OFFICETEL(매핑 없음)은 빈 Set.
    public static Set<String> usageNamesFor(PropertyType type) {
        return USAGE_NAMES_BY_TYPE.getOrDefault(type, Set.of());
    }

    // 아파트·연립다세대의 세대당 추정 면적(gfa/hh_cnt) — 그 외 유형이거나 hh_cnt가 없거나 0이면 계산
    // 불가로 빈 값(호출부가 "필터에서 제외"할지 "건물 전체 면적으로 폴백"할지 각자 결정, F-04 §2.1-a·§2.1-e).
    public static Optional<BigDecimal> estimatedUnitArea(PropertyType type, BigDecimal grossFloorArea, Integer householdCount) {
        boolean householdBased = type == PropertyType.APARTMENT || type == PropertyType.ROW_HOUSE;
        if (!householdBased || householdCount == null || householdCount == 0 || grossFloorArea == null) {
            return Optional.empty();
        }
        return Optional.of(grossFloorArea.divide(BigDecimal.valueOf(householdCount), 2, RoundingMode.HALF_UP));
    }

    // F-04 §2.1-e 메인 표시값 — 세대당 추정 면적이 있으면 그 값, 없으면(비세대형 유형 또는 hh_cnt
    // 없음) 건물 전체 면적(grossFloorArea) 그대로 폴백. 카드에 항상 뭔가는 보여줘야 해서 null 대신 폴백.
    public static BigDecimal displayArea(PropertyType type, BigDecimal grossFloorArea, Integer householdCount) {
        return estimatedUnitArea(type, grossFloorArea, householdCount).orElse(grossFloorArea);
    }
}
