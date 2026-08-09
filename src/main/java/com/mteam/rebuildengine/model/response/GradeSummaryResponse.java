package com.mteam.rebuildengine.model.response;

import com.mteam.rebuildengine.model.read.GradeSummaryReadModel;
import com.mteam.rebuildengine.utils.InvestmentGrade;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// FEATURE_04 §3.1 gradeSummary 스키마. A/B/C/D + NA(정보 부족, §3.3 2026-08-09 등급체계 축소로
// InvestmentGrade의 정식 5번째 값) 전부 포함(0건도 행 유지, F-01 §2.4). enum 선언 순서(A,B,C,D,NA)가
// 그대로 배지 노출 순서 — NA가 항상 맨 끝(FEATURE_04_SEARCH.md §2.1-g "NA는 등급 순위 안에 안 섞음").
public record GradeSummaryResponse(String grade, int count, BigDecimal avgRoi) {

    // BuildingMapper.gradeSummaryForPropertySearch는 매칭되는 건물이 1건도 없는 등급은 행 자체를
    // 안 돌려주므로(GROUP BY), 여기서 5개 등급을 전부 채우고 없는 등급은 0/null로 메운다.
    public static List<GradeSummaryResponse> from(List<GradeSummaryReadModel> readModels) {
        Map<String, GradeSummaryReadModel> byGrade = readModels.stream()
                .collect(Collectors.toMap(GradeSummaryReadModel::grade, Function.identity()));
        return Arrays.stream(InvestmentGrade.values())
                .map(InvestmentGrade::getDisplayName)
                .map(grade -> {
                    GradeSummaryReadModel found = byGrade.get(grade);
                    return found != null
                            ? new GradeSummaryResponse(grade, (int) found.count(), found.avgRoi())
                            : new GradeSummaryResponse(grade, 0, null);
                })
                .toList();
    }

    // 매물 0건(위치/필터에 매칭되는 건물 자체가 없음) 또는 단건 조회(buildingId, §3.1) 등 집계 쿼리를
    // 돌릴 필요가 없는 경우 전 등급 0건으로 응답한다.
    public static List<GradeSummaryResponse> emptySummary() {
        return Arrays.stream(InvestmentGrade.values())
                .map(grade -> new GradeSummaryResponse(grade.getDisplayName(), 0, null))
                .toList();
    }
}
