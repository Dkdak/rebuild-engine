package com.mteam.rebuildengine.model.read;

import java.math.BigDecimal;
import java.time.LocalDate;

// F-09 V1 배치(InvestmentAnalysisBatchService) 전용 — F-08 유사거래 비교(§3.4-B)에 필요한 최소
// 컬럼만 담은 프로젝션. 배치 시작 시 trade 테이블에서 이 형태로 전량(recency 범위 내) 1회만
// 읽어와 TradeStatsIndex에 올린다 — 건물 585,331건을 순회하며 매번 DB에 다시 묻지 않기 위함.
// contractDate는 F-10 "유사 사례"(§2.9) 근거 표시용 — 매칭된 거래를 최신순으로 상위 5건 뽑을 때 사용.
public record TradeStatRow(
        String propertyType,
        String sggNm,
        String bjdongNm,
        BigDecimal areaSqm,
        Integer buildYear,
        BigDecimal price10kWon,
        LocalDate contractDate
) {
}
