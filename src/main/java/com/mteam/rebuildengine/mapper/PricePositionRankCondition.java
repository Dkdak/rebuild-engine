package com.mteam.rebuildengine.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

// §8.17 "시장 내 가격 위치" thisPropertyPercentile 라이브 조회 조건. MarketComparableCondition과
// 필드 구성이 완전히 같고 value(이 매물의 ㎡당가, §8.16 지분거래 판정 적용됨)만 추가된다 — 별도
// 레코드로 두는 이유는 MarketMapper.xml의 comparableTradeCondition 조각을 그대로(파라미터 접두사
// 없이) 재사용하기 위해서다(MyBatis 단일 파라미터 관례).
public record PricePositionRankCondition(
        String propertyType,
        String sggNm,
        String bjdongNm,
        BigDecimal areaMin,
        BigDecimal areaMax,
        Integer buildYearMin,
        Integer buildYearMax,
        LocalDate recencyCutoff,
        BigDecimal value
) {
}
