package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// F-03 대시보드 "리모델링 후보" 집계 — F-09 배치가 552,956건을 순회하며 부산물로 집계해 스냅샷 1행으로
// 저장하고(dashboard_stats_snapshot), 이 API는 그 최신 행을 그대로 읽어 반환한다(2026-08-23, product
// 확정: 실시간 통합 엔드포인트 → 배치+스냅샷으로 전환. 응답 스키마는 그대로 재사용). 후보 정의는
// investment_result.is_remodeling_candidate(verdict='POSSIBLE') 그대로(FEATURE_09_INVESTMENT.md §3.1).
// computedAt은 이 스냅샷을 만든 배치 실행 시각(집계 기준 시각) — "분석 재계산 YYYY-MM-DD" UI 표시가
// 이 값을 그대로 쓴다(product 결정 #4).
//
// districts/distributions는 조합(zoneConfirmed×farSurplusPositive×districtUnrestricted)별로 쪼개
// filters.combinations[] 각 항목 안에 들어간다(2026-08-23, product 재요청 — 좁히기 체크박스를 토글해도
// 분포가 안 바뀌던 스펙 누락 수정). 조합이 상호배타 분할이므로 프론트는 선택된 조합의 count/districts/
// distributions를 그대로 합산해 쓸 수 있다(ROI 평균·산출불가·모집단도 합산 가능 — 단 median은 안 됨,
// 그래서 median은 조합별로 이미 계산된 값을 그대로 쓴다).
public record DashboardStatsResponse(
        LocalDateTime computedAt,
        Funnel funnel,
        Filters filters,
        DataStatus dataStatus
) {
    public record Funnel(long totalBuilding, long analysisTarget, long candidates, long narrowed3, long undeterminedZone) {
    }

    // standalone: 후보(candidates) 모집단 기준 단독 건수. combinations: 좁히기 3개(zoneConfirmed×
    // farSurplusPositive×districtUnrestricted) 조합 8개(실제로는 6개만 발생 — farSurplusPositive=true·
    // zoneConfirmed=false 조합 2개는 구조적으로 발생 불가, floorAreaRatioSurplus가 zoneName 확정 후에만
    // 계산되기 때문. 발생하지 않는 조합은 리스트에 아예 없음 — 소비 측에서 count=0으로 취급).
    public record Filters(long zoneConfirmedCount, long farSurplusPositiveCount, long districtUnrestrictedCount,
                           long candidateTotal, List<Combination> combinations, List<Selection> selections) {
        // districts/distributions는 이 조합에 속한 candidate만의 자체 분포 — pct도 이 조합 자체 합계
        // 기준(narrowed3 전체 기준 아님). "좁히기 3개 모두 적용" 조합(zoneConfirmed·farSurplusPositive·
        // districtUnrestricted 전부 true)의 count가 funnel.narrowed3와 정확히 같다.
        public record Combination(boolean zoneConfirmed, boolean farSurplusPositive, boolean districtUnrestricted,
                                   long count, List<District> districts, Distributions distributions) {
        }

        // 2026-08-23(frontend 요청) — combinations는 상호배타 분할이라 건수 계열(등급·유형·지역·노후도
        // 구간·roi population/notCalculable)은 프론트가 합산해서 쓸 수 있지만, roiAvg·roiMedian·
        // agingAvgAge는 "값의 통계"라 분할을 합쳐서 만들 수 없다(평균은 가중평균이 필요, 중위는 원천
        // 불가). 그래서 실제 체크박스가 가질 수 있는 6가지 상태 각각에 대해 이 3개만 미리 계산해 둔다.
        // null = 그 체크박스가 꺼짐(제약 없음), true = 켜짐 — combinations의 boolean(분할 키)과는
        // 의미가 다르다. 용적률 단독 체크는 UI에서 불가능하므로(용도지역에 종속) 6가지뿐이다.
        public record Selection(Boolean zoneConfirmed, Boolean farSurplusPositive, Boolean districtUnrestricted,
                                 long count, BigDecimal roiAvg, BigDecimal roiMedian, BigDecimal agingAvgAge) {
        }
    }

    // sigunguCd는 legal_dong_code 매칭 실패 시 null(자치구명 자체 매칭 실패는 실질적으로 없음, 25개 전부 존재 확인됨).
    public record District(String sggName, String sigunguCd, long count, BigDecimal pct) {
    }

    public record Distributions(List<GradeCount> grade, Roi roi, List<BuildingTypeCount> buildingType, Aging aging) {
        public record GradeCount(String grade, long count, BigDecimal pct) {
        }

        // 구간은 dashboardStats.ts ROI_DISTRIBUTION 6구간(음수 포함) 그대로 — 등급 경계값(20/10/5%)과
        // 다르다(2026-08-23 product 반려 근거: 등급 경계값을 쓰면 등급 분포와 완전히 같은 숫자가 나와
        // ROI 카드가 등급 카드를 복제하게 됨). avg/notCalculable/population은 조합 간 합산 가능하지만
        // median은 분할을 합쳐 구할 수 없어(percentile_cont 성질) 조합별로 이미 계산된 값을 그대로 쓴다.
        public record Roi(long ltNeg10, long neg10To0, long pos0To10, long pos10To20, long pos20To30, long ge30,
                           long notCalculable, long population, BigDecimal avg, BigDecimal median) {
        }

        // 대장 주용도 기준(단독주택/제1종근생/공동주택/제2종근생/기타 5종).
        public record BuildingTypeCount(String usageGroup, long count, BigDecimal pct) {
        }

        // 노후도 10년 단위 구간 + 평균·30년↑·40년↑ 비율. buildingAgeYears는 KST(앱 서버 로컬 타임존)
        // 기준 LocalDate.now()로 계산(RemodelingServiceImpl과 동일 기준).
        public record Aging(long lt10, long age10To20, long age20To30, long age30To40, long ge40,
                             BigDecimal avgAge, BigDecimal pctGe30, BigDecimal pctGe40, long population) {
        }
    }

    // 조합과 무관 — 후보 정의·좁히기 조합 어느 쪽도 안 건드리는 배치 실행 메타데이터라 그대로 top-level.
    public record DataStatus(LocalDateTime lastBatchRun, LocalDate tradeLatestContractDate, LocalDate permitLatestDate,
                              Matching matching) {
        public record Matching(long tradeBuildingMatched, long tradeBuildingTotal,
                                long permitMatched, long permitTotal,
                                long landuseMatched, long landuseTotal) {
        }
    }
}
