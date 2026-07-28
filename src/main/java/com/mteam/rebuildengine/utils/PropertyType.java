package com.mteam.rebuildengine.utils;

import java.util.Arrays;
import java.util.Optional;

// DOMAIN.md §1 부동산유형 6종.
public enum PropertyType {
    APARTMENT("아파트"),
    ROW_HOUSE("연립다세대"),
    SINGLE_FAMILY("단독다가구"),
    OFFICETEL("오피스텔"),
    COMMERCIAL("상업업무용"),
    INDUSTRIAL("공장창고");

    private final String label;

    PropertyType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<PropertyType> fromLabel(String label) {
        return Arrays.stream(values()).filter(type -> type.label.equals(label)).findFirst();
    }
}
