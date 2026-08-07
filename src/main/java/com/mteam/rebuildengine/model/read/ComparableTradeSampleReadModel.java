package com.mteam.rebuildengine.model.read;

import java.math.BigDecimal;
import java.time.LocalDate;

// F-10 "유사 사례"(§2.9) 근거 표시용 — F-08 §3.4-B 유사거래 비교에 실제로 쓰인 개별 거래 1건.
// 정확한 지번은 담지 않는다(bjdongNm까지만).
public record ComparableTradeSampleReadModel(String bjdongNm, BigDecimal areaSqm, BigDecimal price10kWon,
                                              LocalDate contractDate) {
}
