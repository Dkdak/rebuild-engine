package com.mteam.rebuildengine.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

// §8.17 "거래 활성도" 라이브 조회 조건 — MarketComparableCondition에서 recencyCutoff 하나 대신
// cutoff1y/3y/5y 세 개를 받는다(한 쿼리로 세 기간을 동시에 센다, MarketMapper.xml
// findTradeActivityCounts).
public record TradeActivityCondition(
        String propertyType,
        String sggNm,
        String bjdongNm,
        BigDecimal areaMin,
        BigDecimal areaMax,
        Integer buildYearMin,
        Integer buildYearMax,
        LocalDate cutoff1y,
        LocalDate cutoff3y,
        LocalDate cutoff5y
) {
}
