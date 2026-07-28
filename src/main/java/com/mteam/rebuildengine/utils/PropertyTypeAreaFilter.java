package com.mteam.rebuildengine.utils;

import java.math.BigDecimal;

// F-04 §2.1-a — 부동산유형별 면적 서브필터 한 건. areaMin/areaMax는 해당 type에만 적용된다(전 유형 공유 아님).
public record PropertyTypeAreaFilter(PropertyType type, BigDecimal areaMin, BigDecimal areaMax) {
}
