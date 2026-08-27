package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;

// FEATURE_19_PERSONALIZED_ANALYSIS.md §2.2-a STEP5 "유효연식 참고표" — 연식대별(보정 없음/−10/−15/
// −20년) 유사거래 재조회 결과 한 행. FEATURE_08_MARKET.md §3.7 확장(2026-08-24) — §3.7이 이미
// "입력 면적만 다른 같은 쿼리"로 증축 후 시세를 뽑고 있어, "입력 연식만 다른 같은 쿼리"를 더한 구조다.
// ageAdjustmentYears는 "몇 년 더 젊게 보정했는가"(0/10/15/20) — 실제 준공연도에 이 값을 더한 연도를
// 유사거래 검색의 준공연도 기준으로 쓴다. targetAreaSqm은 이 추정에 쓰인 대상 면적(세대 기반 유형은
// 세대당 면적, 그 외는 증축 후 면적) — 프론트가 ㎡·평 단가를 직접 환산할 수 있게 함께 내려준다.
// ROI는 이 응답에 없다 — F-09 계산 엔진(총투자금 등)까지 필요해 F-19 실측 API 레이어에서 이 값을
// 받아 계산한다(F-08은 시세만 책임진다는 기존 레이어 원칙 유지).
//
// 2026-08-24 재확정(product) — 4행의 완화 단계(estimatedPrice.confidenceLevel)를 가장 넓은 단계
// 하나로 통일한다. "입력 연식만 다른 같은 쿼리"라는 §2.2-a 전제를 지키려면 지역 범위(완화 단계)까지
// 행마다 달라지면 비교 자체가 성립하지 않기 때문 — 정밀도보다 4행 간 비교 가능성을 우선한다.
// insufficientSample은 통일된 단계에서도 비교거래가 하한(10건, FEATURE_19 §5.1 잠정치) 미만이면
// true — 값은 그대로 내려주되 프론트는 "표본 부족"으로 표시하고 클릭 불가 처리한다(억지로 값을
// 숨기지 않는다, 근거 있는 값은 그대로 보여주고 판단은 사용자 몫이라는 원칙 유지).
public record AgeAdjustedPriceResponse(int ageAdjustmentYears, BigDecimal targetAreaSqm,
                                        EstimatedPriceResponse estimatedPrice, boolean insufficientSample) {
}
