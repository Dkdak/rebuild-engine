package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;
import java.time.LocalDate;

// F-08 §3.4-B / F-10 "유사 사례" 페이지(§2.9) — 추정 시세 계산에 실제로 쓰인 개별 거래 근거.
// 정확한 지번은 노출하지 않고 법정동(dong)까지만. matchStage: 0=법정동(§3.5 0단계)/1=구(1단계)/
// 2=범위 확대(2단계) — 그 EstimatedPriceResponse가 어느 완화 단계에서 나왔는지와 항상 같은 값.
public record ComparableTradeResponse(String dong, BigDecimal area, BigDecimal price, LocalDate contractDate,
                                       int matchStage) {
}
