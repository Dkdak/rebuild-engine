package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;
import java.util.List;

// FEATURE_04 §3.1 gradeSummary 스키마. A+~D 6등급 전부 포함(0건도 행 유지, F-01 §2.4).
public record GradeSummaryResponse(String grade, int count, BigDecimal avgRoi) {

    public static final List<String> GRADES = List.of("A+", "A", "B+", "B", "C", "D");

    // 1차엔 F-09 등급 산정 로직이 없어 전 등급 0건 고정(F-04 §4 "F-09 미완료 상태" 예외 처리).
    public static List<GradeSummaryResponse> emptySummary() {
        return GRADES.stream().map(grade -> new GradeSummaryResponse(grade, 0, null)).toList();
    }
}
