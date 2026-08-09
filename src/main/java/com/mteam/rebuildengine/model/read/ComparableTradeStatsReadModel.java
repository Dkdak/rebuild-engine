package com.mteam.rebuildengine.model.read;

import java.math.BigDecimal;

// F-08 §3.4-B — 조건을 만족하는 유사 거래들의 ㎡당 가격 중앙값 + 건수. comparableCount가 0이면
// medianPricePerSqm은 null(Service가 §3.5 다음 단계로 넘어가는 기준).
// p25/p75PricePerSqm(§8.17 "시장 내 가격 위치" 추가, 2026-08-09) — median과 완전히 같은 모집단에서
// percentile_cont(0.25)/(0.75)로 뽑는다. comparableCount가 0이면 이 둘도 null.
public record ComparableTradeStatsReadModel(BigDecimal p25PricePerSqm, BigDecimal medianPricePerSqm,
                                             BigDecimal p75PricePerSqm, long comparableCount) {
}
