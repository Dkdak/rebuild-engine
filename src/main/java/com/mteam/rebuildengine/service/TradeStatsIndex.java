package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.read.ComparableTradeSampleReadModel;
import com.mteam.rebuildengine.model.read.ComparableTradeSearchResult;
import com.mteam.rebuildengine.model.read.ComparableTradeStatsReadModel;
import com.mteam.rebuildengine.model.read.PriceTrendPointReadModel;
import com.mteam.rebuildengine.model.read.TradeStatRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// F-09 V1 배치(InvestmentAnalysisBatchService) 전용 — F-08 §3.4-B/§3.5 유사거래 비교를 건물마다
// DB에 다시 묻지 않고, 배치 시작 시 1회 로드한 trade 데이터(TradeStatRow)를 메모리에서 조회한다.
// stats()의 반환 타입·판정 기준(면적/연식 범위, percentile_cont(0.5)와 동일한 중앙값 계산)은
// MarketMapper.findComparableTradeStats()와 완전히 동일해야 한다 — 결과가 갈리면 안 되므로, 여기서는
// "어디서 읽어오는지"만 다르고 "무엇을 계산하는지"는 그대로 둔다.
// F-10 "유사 사례"(§2.9) 근거용 샘플(최대 5건, 계약일 최신순)도 같은 필터링 결과에서 함께 뽑는다.
// 인덱스 2종(2026-08-08 성능 개선, "모든 배치는 메모리/맵 활용해 3분 안에" 원칙): §3.5 0단계(법정동)는
// (유형,구,동) 3단 키로 바로 좁혀서 그 동 안의 거래만 스캔하고, 1/2단계(구 전체)만 (유형,구) 2단 키를
// 쓴다 — 대부분의 건물이 0단계에서 해결되는데(§3.6 실측), 그동안은 구 전체 후보를 다 스캔하고 동 이름을
// 한 건씩 비교하고 있었다. 동 단위로 미리 쪼개두면 그 스캔이 필요 없다.
public final class TradeStatsIndex {

    private static final int SAMPLE_LIMIT = 5;

    private final Map<String, List<TradeStatRow>> byTypeAndSgg;
    private final Map<String, List<TradeStatRow>> byTypeAndSggAndDong;

    private TradeStatsIndex(Map<String, List<TradeStatRow>> byTypeAndSgg, Map<String, List<TradeStatRow>> byTypeAndSggAndDong) {
        this.byTypeAndSgg = byTypeAndSgg;
        this.byTypeAndSggAndDong = byTypeAndSggAndDong;
    }

    public static TradeStatsIndex load(List<TradeStatRow> rows) {
        return new TradeStatsIndex(
                rows.stream().collect(Collectors.groupingBy(TradeStatsIndex::sggKey)),
                rows.stream().collect(Collectors.groupingBy(TradeStatsIndex::dongKey)));
    }

    private static String sggKey(TradeStatRow row) {
        return sggKey(row.propertyType(), row.sggNm());
    }

    private static String sggKey(String propertyType, String sggNm) {
        return propertyType + "|" + sggNm;
    }

    private static String dongKey(TradeStatRow row) {
        return dongKey(row.propertyType(), row.sggNm(), row.bjdongNm());
    }

    private static String dongKey(String propertyType, String sggNm, String bjdongNm) {
        return propertyType + "|" + sggNm + "|" + bjdongNm;
    }

    // bjdongNm이 null이면 동 조건 생략(MarketMapper.xml의 <if test="bjdongNm != null"> 관례와 동일).
    public ComparableTradeSearchResult stats(String propertyType, String sggNm, String bjdongNm,
                                              BigDecimal areaMin, BigDecimal areaMax,
                                              Integer buildYearMin, Integer buildYearMax) {
        return toComparable(filterCandidates(propertyType, sggNm, bjdongNm, areaMin, areaMax, buildYearMin, buildYearMax));
    }

    // F-08 §3.8 "시세 추이" — stats()와 완전히 같은 필터링 결과를 계약월(YYYY-MM)별로 묶어 ㎡당 가격
    // 중앙값을 낸다. MarketMapper.findMonthlyPriceTrend()의 HAVING COUNT(*)>=3과 동일하게, 표본이
    // 3건 미만인 달은 결과에서 뺀다(medianPricePerSqm을 null로 채우지 않고 아예 생략).
    private static final int MIN_MONTH_TRADE_COUNT = 3;

    public List<PriceTrendPointReadModel> monthlyTrend(String propertyType, String sggNm, String bjdongNm,
                                                         BigDecimal areaMin, BigDecimal areaMax,
                                                         Integer buildYearMin, Integer buildYearMax) {
        return toMonthlyTrend(filterCandidates(propertyType, sggNm, bjdongNm, areaMin, areaMax, buildYearMin, buildYearMax));
    }

    // estimatedPrice(stats())와 priceTrend(monthlyTrend())는 §3.4-B 0/1단계에서 완전히 같은 조건으로
    // 후보를 필터링한다 — MarketServiceImpl이 그 두 단계에서는 filterCandidates()를 한 번만 돌리고
    // 결과를 이 메서드로 통계+월별추이 양쪽에 동시에 파생시킨다(2026-08-08 성능 개선, 585K건 배치에서
    // 건물당 필터링 스캔 최대 5회→최대 3회로 감소).
    public record StageResult(ComparableTradeSearchResult comparable, List<PriceTrendPointReadModel> monthlyTrend) {
    }

    public StageResult stageResult(String propertyType, String sggNm, String bjdongNm,
                                    BigDecimal areaMin, BigDecimal areaMax,
                                    Integer buildYearMin, Integer buildYearMax) {
        List<TradeStatRow> matched = filterCandidates(propertyType, sggNm, bjdongNm, areaMin, areaMax, buildYearMin, buildYearMax);
        return new StageResult(toComparable(matched), toMonthlyTrend(matched));
    }

    private static ComparableTradeSearchResult toComparable(List<TradeStatRow> matched) {
        if (matched.isEmpty()) {
            return new ComparableTradeSearchResult(new ComparableTradeStatsReadModel(null, 0), List.of());
        }
        List<BigDecimal> ratios = matched.stream().map(TradeStatsIndex::pricePerSqm).toList();
        ComparableTradeStatsReadModel stats = new ComparableTradeStatsReadModel(medianOf(ratios), ratios.size());
        List<ComparableTradeSampleReadModel> samples = matched.stream()
                .sorted(Comparator.comparing(TradeStatRow::contractDate).reversed())
                .limit(SAMPLE_LIMIT)
                .map(row -> new ComparableTradeSampleReadModel(row.bjdongNm(), row.areaSqm(), row.price10kWon(), row.contractDate()))
                .toList();
        return new ComparableTradeSearchResult(stats, samples);
    }

    private static List<PriceTrendPointReadModel> toMonthlyTrend(List<TradeStatRow> matched) {
        Map<String, List<TradeStatRow>> byMonth = matched.stream().collect(Collectors.groupingBy(TradeStatsIndex::monthOf));
        return byMonth.entrySet().stream()
                .filter(e -> e.getValue().size() >= MIN_MONTH_TRADE_COUNT)
                .map(e -> new PriceTrendPointReadModel(e.getKey(),
                        medianOf(e.getValue().stream().map(TradeStatsIndex::pricePerSqm).toList()),
                        e.getValue().size()))
                .sorted(Comparator.comparing(PriceTrendPointReadModel::month))
                .toList();
    }

    private static String monthOf(TradeStatRow row) {
        return "%04d-%02d".formatted(row.contractDate().getYear(), row.contractDate().getMonthValue());
    }

    private static BigDecimal pricePerSqm(TradeStatRow row) {
        return row.price10kWon().divide(row.areaSqm(), 10, RoundingMode.HALF_UP);
    }

    private List<TradeStatRow> filterCandidates(String propertyType, String sggNm, String bjdongNm,
                                                 BigDecimal areaMin, BigDecimal areaMax,
                                                 Integer buildYearMin, Integer buildYearMax) {
        // bjdongNm이 있으면(0단계) 이미 그 동으로 좁혀진 버킷을 쓴다 — 동 이름 비교를 후보 전체에 대해
        // 할 필요가 없다. null이면(1/2단계, 구 전체) 기존처럼 구 단위 버킷 전체를 스캔.
        List<TradeStatRow> candidates = bjdongNm != null
                ? byTypeAndSggAndDong.getOrDefault(dongKey(propertyType, sggNm, bjdongNm), List.of())
                : byTypeAndSgg.getOrDefault(sggKey(propertyType, sggNm), List.of());
        List<TradeStatRow> matched = new ArrayList<>();
        for (TradeStatRow row : candidates) {
            // areaMin/areaMax가 둘 다 null이면(§3.5 3/4단계, 법정동·구 × 유형 평당가) 면적 조건 자체를
            // 생략한다 — buildYear와 같은 null-스킵 관례.
            if (areaMin != null && row.areaSqm().compareTo(areaMin) < 0) {
                continue;
            }
            if (areaMax != null && row.areaSqm().compareTo(areaMax) > 0) {
                continue;
            }
            if (buildYearMin != null && (row.buildYear() == null || row.buildYear() < buildYearMin)) {
                continue;
            }
            if (buildYearMax != null && (row.buildYear() == null || row.buildYear() > buildYearMax)) {
                continue;
            }
            matched.add(row);
        }
        return matched;
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
