package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;
import java.util.List;

// F-08 §3.4-B/§3.6 "추정 시세" — value는 confidenceLevel==UNAVAILABLE이면 null.
// comparableTrades: F-10 "유사 사례"(§2.9) 근거용, 최대 5건·계약일 최신순(2026-08-08 추가).
// conservativeValue/optimisticValue(§3.9 "미래가치" 3-way 시나리오, 2026-08-08 추가): comparableTrades
// 중 ㎡당가격이 가장 낮은/높은 건을 대상 면적에 곱한 값 — comparableTrades가 2건 미만이면(최저=최고=
// 중앙값이라 시나리오 의미 없음) 둘 다 null. postRemodelEstimatedPrice 전용으로 기획됐지만(§3.9),
// toEstimatedPrice()가 estimatedPrice/postRemodelEstimatedPrice 둘 다 공유하는 단일 계산 지점이라
// 같은 로직으로 채워진다 — estimatedPrice에도 값이 실리는 건 부작용이 아니라 의도된 단순화.
public record EstimatedPriceResponse(BigDecimal value, ConfidenceLevel confidenceLevel, long comparableCount,
                                      List<ComparableTradeResponse> comparableTrades,
                                      BigDecimal conservativeValue, BigDecimal optimisticValue) {

    public static EstimatedPriceResponse unavailable() {
        return new EstimatedPriceResponse(null, ConfidenceLevel.UNAVAILABLE, 0, List.of(), null, null);
    }
}
