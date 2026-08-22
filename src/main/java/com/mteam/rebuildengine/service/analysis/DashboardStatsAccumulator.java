package com.mteam.rebuildengine.service.analysis;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.model.response.DashboardStatsResponse;
import com.mteam.rebuildengine.model.response.RemodelingBasisResponse;
import com.mteam.rebuildengine.model.response.RemodelingResultResponse;
import com.mteam.rebuildengine.model.response.RemodelingVerdict;
import com.mteam.rebuildengine.utils.InvestmentGrade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

// F-03 대시보드 "리모델링 후보" 집계(2026-08-23, product 확정 — 배치+스냅샷 전환, 이어서 같은 날 조합별
// 분포 분할 재요청) — F-09 배치가 552,956건을 순회하는 김에(record()) 부산물로 집계한다. 별도 전체
// 스캔이 추가되지 않는다. 좁히기 3개(zoneConfirmed×farSurplusPositive×districtUnrestricted) 조합별로
// district/등급/유형/노후도/ROI 분포를 전부 따로 계산한다 — 체크박스 토글마다 프론트가 선택된 조합만
// 합산해서 쓸 수 있게 하려는 목적(조합이 상호배타 분할이라 합산이 정확값). record()는 배치의 페이지-내
// 병렬 스레드(최대 8개, InvestmentAnalysisBatchServiceImpl)에서 동시에 호출되므로 모든 필드가
// 스레드 세이프해야 한다.
public class DashboardStatsAccumulator {

    private static final List<String> KNOWN_USAGE_GROUPS =
            List.of("단독주택", "제1종근린생활시설", "공동주택", "제2종근린생활시설");
    private static final String OTHER_USAGE_GROUP = "기타";

    // 배치 실행 몇 분 동안 자정(KST)을 넘어가도 노후도 기준일이 흔들리지 않도록 배치 시작 시 1회만 고정.
    private final LocalDate ageAnchor = LocalDate.now();

    private final AtomicLong candidates = new AtomicLong();
    private final AtomicLong undeterminedZone = new AtomicLong();
    private final AtomicLong zoneConfirmedCount = new AtomicLong();
    private final AtomicLong farSurplusPositiveCount = new AtomicLong();
    private final AtomicLong districtUnrestrictedCount = new AtomicLong();

    private final Map<CombinationKey, CombinationAccumulator> perCombination = new ConcurrentHashMap<>();

    private record CombinationKey(boolean zoneConfirmed, boolean farSurplusPositive, boolean districtUnrestricted) {
    }

    // 프론트 체크박스가 실제로 가질 수 있는 6가지 상태(2026-08-23, frontend 요청) — null은 "그 체크박스
    // 꺼짐"(제약 없음). 용적률만 단독 체크는 UI에서 용도지역에 종속돼 불가능하므로 6가지뿐이다.
    private static final List<SelectionDef> SELECTION_DEFS = List.of(
            new SelectionDef(null, null, null),
            new SelectionDef(true, null, null),
            new SelectionDef(null, null, true),
            new SelectionDef(true, null, true),
            new SelectionDef(true, true, null),
            new SelectionDef(true, true, true)
    );

    private record SelectionDef(Boolean zoneConfirmed, Boolean farSurplusPositive, Boolean districtUnrestricted) {
        boolean matches(CombinationKey key) {
            return (zoneConfirmed == null || zoneConfirmed == key.zoneConfirmed())
                    && (farSurplusPositive == null || farSurplusPositive == key.farSurplusPositive())
                    && (districtUnrestricted == null || districtUnrestricted == key.districtUnrestricted());
        }
    }

    public void record(BuildingEntity building, RemodelingResultResponse remodeling, InvestmentGrade grade, BigDecimal roi) {
        if (remodeling.verdict() != RemodelingVerdict.POSSIBLE) {
            return;
        }
        candidates.incrementAndGet();

        RemodelingBasisResponse basis = remodeling.basis();
        boolean zoneConfirmed = basis.zoneName() != null;
        boolean farSurplusPositive = basis.floorAreaRatioSurplus() != null && basis.floorAreaRatioSurplus().signum() > 0;
        boolean districtUnrestricted = basis.districtNames().isEmpty();

        if (zoneConfirmed) {
            zoneConfirmedCount.incrementAndGet();
        } else {
            undeterminedZone.incrementAndGet();
        }
        if (farSurplusPositive) {
            farSurplusPositiveCount.incrementAndGet();
        }
        if (districtUnrestricted) {
            districtUnrestrictedCount.incrementAndGet();
        }

        perCombination.computeIfAbsent(new CombinationKey(zoneConfirmed, farSurplusPositive, districtUnrestricted),
                key -> new CombinationAccumulator()).record(building, grade, roi, ageAnchor);
    }

    // 배치가 이미 걸러낸 뒤(computeRow)라 record()만으론 못 만드는 값(totalBuilding/dataStatus)은
    // 배치 끝에 별도 supplementary 쿼리로 채워 여기서 최종 조립한다.
    public DashboardStatsResponse toResponse(long totalBuilding, long analysisTarget, LocalDateTime computedAt,
                                              LocalDate tradeLatestContractDate, LocalDate permitLatestDate,
                                              long tradeBuildingMatched, long tradeBuildingTotal,
                                              long permitMatched, long permitTotal,
                                              long landuseMatched, long landuseTotal,
                                              Function<String, String> sigunguCdResolver) {
        long narrowed3 = perCombination.getOrDefault(
                new CombinationKey(true, true, true), CombinationAccumulator.EMPTY).count.get();

        List<DashboardStatsResponse.Filters.Combination> combinations = perCombination.entrySet().stream()
                .sorted(Comparator.<Map.Entry<CombinationKey, CombinationAccumulator>>comparingInt(e -> e.getKey().zoneConfirmed() ? 1 : 0)
                        .thenComparingInt(e -> e.getKey().farSurplusPositive() ? 1 : 0)
                        .thenComparingInt(e -> e.getKey().districtUnrestricted() ? 1 : 0))
                .map(e -> e.getValue().toCombination(e.getKey(), sigunguCdResolver))
                .toList();

        return new DashboardStatsResponse(
                computedAt,
                new DashboardStatsResponse.Funnel(totalBuilding, analysisTarget, candidates.get(), narrowed3, undeterminedZone.get()),
                new DashboardStatsResponse.Filters(zoneConfirmedCount.get(), farSurplusPositiveCount.get(),
                        districtUnrestrictedCount.get(), candidates.get(), combinations, buildSelections()),
                new DashboardStatsResponse.DataStatus(computedAt, tradeLatestContractDate, permitLatestDate,
                        new DashboardStatsResponse.DataStatus.Matching(tradeBuildingMatched, tradeBuildingTotal,
                                permitMatched, permitTotal, landuseMatched, landuseTotal))
        );
    }

    // roiAvg/roiMedian/agingAvgAge는 값의 통계라 조합(분할)을 합쳐서 만들 수 없다 — 6가지 체크박스
    // 상태 각각에 해당하는 원시 조합들을 모아 그 자리에서 다시 계산한다. 후보 341,614건이 6번 이내로만
    // 재정렬되므로 비용은 무시할 수준(추가 스캔 없음, 배치 끝에 메모리 안에서 1회 처리).
    private List<DashboardStatsResponse.Filters.Selection> buildSelections() {
        return SELECTION_DEFS.stream().map(def -> {
            List<CombinationAccumulator> matching = perCombination.entrySet().stream()
                    .filter(e -> def.matches(e.getKey()))
                    .map(Map.Entry::getValue)
                    .toList();

            long count = matching.stream().mapToLong(c -> c.count.get()).sum();

            List<BigDecimal> mergedRoi = new ArrayList<>();
            matching.forEach(c -> mergedRoi.addAll(c.roiValues));
            Collections.sort(mergedRoi);
            BigDecimal roiAvg = average(mergedRoi);
            BigDecimal roiMedian = median(mergedRoi);

            long ageSum = matching.stream().mapToLong(c -> c.agingAgeSum.get()).sum();
            long agePopulation = matching.stream().mapToLong(c -> c.agingPopulation.get()).sum();
            BigDecimal agingAvgAge = agePopulation == 0 ? null
                    : BigDecimal.valueOf(ageSum).divide(BigDecimal.valueOf(agePopulation), 1, RoundingMode.HALF_UP);

            return new DashboardStatsResponse.Filters.Selection(def.zoneConfirmed(), def.farSurplusPositive(),
                    def.districtUnrestricted(), count, roiAvg, roiMedian, agingAvgAge);
        }).toList();
    }

    private static BigDecimal average(List<BigDecimal> values) {
        return values.isEmpty() ? null
                : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal median(List<BigDecimal> sorted) {
        int n = sorted.size();
        if (n == 0) {
            return null;
        }
        if (n % 2 == 1) {
            return sorted.get(n / 2).setScale(2, RoundingMode.HALF_UP);
        }
        return sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private static String classifyBuildingType(String mnUsgCdNm) {
        return KNOWN_USAGE_GROUPS.contains(mnUsgCdNm) ? mnUsgCdNm : OTHER_USAGE_GROUP;
    }

    private static BigDecimal pct(long count, long total) {
        return total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(count * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }

    // 조합(zoneConfirmed×farSurplusPositive×districtUnrestricted) 하나의 자체 집계 — district/등급/
    // 유형/노후도/ROI 전부 이 조합에 속한 candidate만 대상. pct는 이 조합 자체 합계 기준(전체 후보
    // 기준 아님) — 조합마다 독립된 분포이기 때문.
    private static final class CombinationAccumulator {
        private static final CombinationAccumulator EMPTY = new CombinationAccumulator();

        private final AtomicLong count = new AtomicLong();
        private final Map<String, AtomicLong> districtCounts = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> gradeCounts = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> buildingTypeCounts = new ConcurrentHashMap<>();

        private final AtomicLong roiLtNeg10 = new AtomicLong();
        private final AtomicLong roiNeg10To0 = new AtomicLong();
        private final AtomicLong roiPos0To10 = new AtomicLong();
        private final AtomicLong roiPos10To20 = new AtomicLong();
        private final AtomicLong roiPos20To30 = new AtomicLong();
        private final AtomicLong roiGe30 = new AtomicLong();
        private final AtomicLong roiNotCalculable = new AtomicLong();
        // 스트리밍 중앙값은 불가능(percentile_cont는 전체 정렬이 필요) — 조합별로 모아 배치 끝에 1회
        // 정렬한다(전체 후보 341,614건이 6개 조합으로 나뉘므로 조합당 최대 그 이하).
        private final List<BigDecimal> roiValues = Collections.synchronizedList(new ArrayList<>());

        private final AtomicLong agingLt10 = new AtomicLong();
        private final AtomicLong agingAge10To20 = new AtomicLong();
        private final AtomicLong agingAge20To30 = new AtomicLong();
        private final AtomicLong agingAge30To40 = new AtomicLong();
        private final AtomicLong agingGe40 = new AtomicLong();
        private final AtomicLong agingAgeSum = new AtomicLong();
        private final AtomicLong agingPopulation = new AtomicLong();

        void record(BuildingEntity building, InvestmentGrade grade, BigDecimal roi, LocalDate ageAnchor) {
            count.incrementAndGet();
            districtCounts.computeIfAbsent(building.getSggCdNm(), key -> new AtomicLong()).incrementAndGet();
            gradeCounts.computeIfAbsent(grade.getDisplayName(), key -> new AtomicLong()).incrementAndGet();
            buildingTypeCounts.computeIfAbsent(classifyBuildingType(building.getMnUsgCdNm()), key -> new AtomicLong()).incrementAndGet();

            if (roi == null) {
                roiNotCalculable.incrementAndGet();
            } else {
                roiValues.add(roi);
                bucketRoi(roi);
            }

            if (building.getUseAprvYmd() != null) {
                int ageYears = Period.between(building.getUseAprvYmd(), ageAnchor).getYears();
                agingPopulation.incrementAndGet();
                agingAgeSum.addAndGet(ageYears);
                bucketAge(ageYears);
            }
        }

        private void bucketRoi(BigDecimal roi) {
            if (roi.compareTo(BigDecimal.valueOf(-10)) < 0) {
                roiLtNeg10.incrementAndGet();
            } else if (roi.compareTo(BigDecimal.ZERO) < 0) {
                roiNeg10To0.incrementAndGet();
            } else if (roi.compareTo(BigDecimal.TEN) < 0) {
                roiPos0To10.incrementAndGet();
            } else if (roi.compareTo(BigDecimal.valueOf(20)) < 0) {
                roiPos10To20.incrementAndGet();
            } else if (roi.compareTo(BigDecimal.valueOf(30)) < 0) {
                roiPos20To30.incrementAndGet();
            } else {
                roiGe30.incrementAndGet();
            }
        }

        private void bucketAge(int years) {
            if (years < 10) {
                agingLt10.incrementAndGet();
            } else if (years < 20) {
                agingAge10To20.incrementAndGet();
            } else if (years < 30) {
                agingAge20To30.incrementAndGet();
            } else if (years < 40) {
                agingAge30To40.incrementAndGet();
            } else {
                agingGe40.incrementAndGet();
            }
        }

        DashboardStatsResponse.Filters.Combination toCombination(CombinationKey key, Function<String, String> sigunguCdResolver) {
            return new DashboardStatsResponse.Filters.Combination(key.zoneConfirmed(), key.farSurplusPositive(),
                    key.districtUnrestricted(), count.get(), buildDistricts(sigunguCdResolver), buildDistributions());
        }

        private List<DashboardStatsResponse.District> buildDistricts(Function<String, String> sigunguCdResolver) {
            long total = count.get();
            return districtCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, AtomicLong>comparingByValue(Comparator.comparingLong(AtomicLong::get)).reversed())
                    .map(e -> new DashboardStatsResponse.District(e.getKey(), sigunguCdResolver.apply(e.getKey()),
                            e.getValue().get(), pct(e.getValue().get(), total)))
                    .toList();
        }

        private DashboardStatsResponse.Distributions buildDistributions() {
            long gradeTotal = gradeCounts.values().stream().mapToLong(AtomicLong::get).sum();
            List<DashboardStatsResponse.Distributions.GradeCount> grade = gradeCounts.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> new DashboardStatsResponse.Distributions.GradeCount(e.getKey(), e.getValue().get(), pct(e.getValue().get(), gradeTotal)))
                    .toList();

            long buildingTypeTotal = buildingTypeCounts.values().stream().mapToLong(AtomicLong::get).sum();
            List<DashboardStatsResponse.Distributions.BuildingTypeCount> buildingType = buildingTypeCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, AtomicLong>comparingByValue(Comparator.comparingLong(AtomicLong::get)).reversed())
                    .map(e -> new DashboardStatsResponse.Distributions.BuildingTypeCount(e.getKey(), e.getValue().get(), pct(e.getValue().get(), buildingTypeTotal)))
                    .toList();

            return new DashboardStatsResponse.Distributions(grade, buildRoi(), buildingType, buildAging());
        }

        private DashboardStatsResponse.Distributions.Roi buildRoi() {
            List<BigDecimal> sorted = new ArrayList<>(roiValues);
            Collections.sort(sorted);
            return new DashboardStatsResponse.Distributions.Roi(roiLtNeg10.get(), roiNeg10To0.get(), roiPos0To10.get(),
                    roiPos10To20.get(), roiPos20To30.get(), roiGe30.get(), roiNotCalculable.get(), sorted.size(),
                    average(sorted), median(sorted));
        }

        private DashboardStatsResponse.Distributions.Aging buildAging() {
            long population = agingPopulation.get();
            BigDecimal avgAge = population == 0 ? null
                    : BigDecimal.valueOf(agingAgeSum.get()).divide(BigDecimal.valueOf(population), 1, RoundingMode.HALF_UP);
            return new DashboardStatsResponse.Distributions.Aging(agingLt10.get(), agingAge10To20.get(), agingAge20To30.get(),
                    agingAge30To40.get(), agingGe40.get(), avgAge,
                    pct(agingAge30To40.get() + agingGe40.get(), population), pct(agingGe40.get(), population), population);
        }
    }
}
