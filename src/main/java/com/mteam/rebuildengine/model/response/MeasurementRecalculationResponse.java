package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;

// F-19 재계산 결과(FEATURE_19_PERSONALIZED_ANALYSIS.md §3.3) — F-09 ROI 공식
// `(미래가치−총투자금)/총투자금×100` 그대로, 입력만 실측값 우선(없으면 공공데이터 추정치)으로
// 바꿔치기한다. 등급(A~D)은 여기 없다 — 공공데이터 기준 하나로 유지하고 F-19에서 재계산하지 않는다
// (product 확정). totalInvestmentMeasured/projectedValueMeasured는 그 값이 실측 기반인지 표시
// (F-10 CASE2 리포트 §6이 "값별 추정/실측 구분"을 요구하는 것과 같은 목적).
//
// additionalBuildableAreaSqm/constructionEstimate/purchasePrice(2026-08-24 추가, §2.2-b 규칙 2 —
// "화면을 열면 전 항목이 리포트 추정치로 이미 채워져 있다") — ROI를 만드는 4대 입력값 중 미래가치는
// projectedValue가 이미 커버해서(futureValue와 동일값) 나머지 3개만 추가한다. 전부 measured=false여도
// value는 채워진다(공공데이터 추정치) — 실측이 없다고 값 자체를 비우지 않는다.
//
// isHousing(2026-08-24 추가, LAW-003 §1-a 확정) — 취득세 중과(지방세법 제13조의2)가 "주택"만
// 대상이라, 프론트가 STEP4 "취득 주체" 항목을 이 매물 유형에서 보여줄지 판단하는 데 필요. building만
// 있으면 정해지는 값이라 위 값들이 전부 unavailable이어도(재계산 실패) 항상 채워진다.
//
// verdict/verdictReason(2026-08-27 추가, product 확정 — "B: NOT_POSSIBLE이어도 계산하고 경고를
// 얹는다") — F-06 리모델링 판정(허용연한 게이트 등)과 무관하게 STEP1/2 실측값 기준으로 항상 계산한다
// (§2.2-b 규칙 2, 사용자가 입력했는데 화면이 비면 "반영 안 됐나" 오인 방지 — 실제로 이 오인에서
// 이 결정이 나왔다). verdict가 NOT_POSSIBLE일 때만 verdictReason이 채워지고, 프론트는 그 문구로
// "아래 숫자는 요건을 충족했다고 가정한 값입니다" 경고를 밴드 위에 띄운다. remodeling 조회 자체가
// 안 되는 드문 경우(건물 조회 실패)만 둘 다 null.
//
// householdCountMissing(2026-08-27 추가, §2.3-f) — 세대기반 유형(아파트·연립다세대)인데 건축물대장에
// 세대수가 없어 매입가·미래가치의 세대기반 계산이 막힌 상태. isHousing과 같은 이유로 building만으로
// 정해지고, computeRecalculation의 어느 분기에서도 값이 있다. 증축면적·공사비는 이 값과 무관하게
// 계산되므로(§2.2-b 규칙 2, verdict 때와 같은 원칙 — 계산 가능한 칸까지 같이 비우지 않는다) 각 필드는
// 항상 독립적으로 채워지거나 null이다.
//
// theoreticalAdditionalBuildableAreaSqm(2026-08-27 추가, product 요청) — STEP1 저장 상한 기준 이론상
// 증축 상한, STEP2 실측 여부와 무관하게 항상 채워진다. additionalBuildableAreaSqm은 STEP2가 실측되면
// 그 값으로 바뀌어서(§3.3 "입력 소스만 바꿔치기") 이 값과 달라질 수 있다 — STEP2 화면의 "N㎡ 남김"
// 표시와 검토값이 상한을 넘는 모순 판정 둘 다 이 필드가 비교 기준이다.
public record MeasurementRecalculationResponse(
        BigDecimal totalInvestment, boolean totalInvestmentMeasured,
        BigDecimal projectedValue, boolean projectedValueMeasured,
        BigDecimal expectedProfit,
        BigDecimal roi,
        ValuedField<BigDecimal> additionalBuildableAreaSqm,
        ValuedField<BigDecimal> constructionEstimate,
        ValuedField<BigDecimal> purchasePrice,
        boolean isHousing,
        RemodelingVerdict verdict,
        String verdictReason,
        boolean householdCountMissing,
        BigDecimal theoreticalAdditionalBuildableAreaSqm
) {
    public static MeasurementRecalculationResponse unavailable(boolean isHousing) {
        return unavailable(isHousing, null, null, false);
    }

    public static MeasurementRecalculationResponse unavailable(boolean isHousing, RemodelingVerdict verdict, String verdictReason,
                                                                  boolean householdCountMissing) {
        ValuedField<BigDecimal> empty = new ValuedField<>(null, false);
        return new MeasurementRecalculationResponse(null, false, null, false, null, null, empty, empty, empty,
                isHousing, verdict, verdictReason, householdCountMissing, null);
    }
}
