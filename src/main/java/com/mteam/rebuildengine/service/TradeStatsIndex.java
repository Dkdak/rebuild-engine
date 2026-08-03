package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.read.ComparableTradeStatsReadModel;
import com.mteam.rebuildengine.model.read.TradeStatRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// F-09 V1 배치(InvestmentAnalysisBatchService) 전용 — F-08 §3.4-B/§3.5 유사거래 비교를 건물마다
// DB에 다시 묻지 않고, 배치 시작 시 1회 로드한 trade 데이터(TradeStatRow)를 (유형, 구) 단위로
// 묶어 메모리에서 조회한다. stats()의 반환 타입·판정 기준(면적/연식 범위, percentile_cont(0.5)와
// 동일한 중앙값 계산)은 MarketMapper.findComparableTradeStats()와 완전히 동일해야 한다 — 결과가
// 갈리면 안 되므로, 여기서는 "어디서 읽어오는지"만 다르고 "무엇을 계산하는지"는 그대로 둔다.
public final class TradeStatsIndex {

    private final Map<String, List<TradeStatRow>> byTypeAndSgg;

    private TradeStatsIndex(Map<String, List<TradeStatRow>> byTypeAndSgg) {
        this.byTypeAndSgg = byTypeAndSgg;
    }

    public static TradeStatsIndex load(List<TradeStatRow> rows) {
        return new TradeStatsIndex(rows.stream().collect(Collectors.groupingBy(TradeStatsIndex::key)));
    }

    private static String key(TradeStatRow row) {
        return key(row.propertyType(), row.sggNm());
    }

    private static String key(String propertyType, String sggNm) {
        return propertyType + "|" + sggNm;
    }

    // bjdongNm이 null이면 동 조건 생략(MarketMapper.xml의 <if test="bjdongNm != null"> 관례와 동일).
    public ComparableTradeStatsReadModel stats(String propertyType, String sggNm, String bjdongNm,
                                                BigDecimal areaMin, BigDecimal areaMax,
                                                Integer buildYearMin, Integer buildYearMax) {
        List<TradeStatRow> candidates = byTypeAndSgg.getOrDefault(key(propertyType, sggNm), List.of());
        List<BigDecimal> ratios = new ArrayList<>();
        for (TradeStatRow row : candidates) {
            if (bjdongNm != null && !bjdongNm.equals(row.bjdongNm())) {
                continue;
            }
            if (row.areaSqm().compareTo(areaMin) < 0 || row.areaSqm().compareTo(areaMax) > 0) {
                continue;
            }
            if (buildYearMin != null && (row.buildYear() == null || row.buildYear() < buildYearMin)) {
                continue;
            }
            if (buildYearMax != null && (row.buildYear() == null || row.buildYear() > buildYearMax)) {
                continue;
            }
            ratios.add(row.price10kWon().divide(row.areaSqm(), 10, RoundingMode.HALF_UP));
        }
        if (ratios.isEmpty()) {
            return new ComparableTradeStatsReadModel(null, 0);
        }
        return new ComparableTradeStatsReadModel(medianOf(ratios), ratios.size());
    }

    // Postgres percentile_cont(0.5) WITHIN GROUP과 동일 — 정렬 후 홀수면 중간값, 짝수면 중간 두 값의 평균.
    private static BigDecimal medianOf(List<BigDecimal> ratios) {
        List<BigDecimal> sorted = ratios.stream().sorted().toList();
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2), 10, RoundingMode.HALF_UP);
    }
}
