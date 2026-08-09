package com.mteam.rebuildengine.utils;

import com.mteam.rebuildengine.model.response.ConfidenceLevel;
import com.mteam.rebuildengine.model.response.EstimatedPriceResponse;
import com.mteam.rebuildengine.model.response.RecentTradeResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;

// FEATURE.md §8.16(2026-08-08 발견, 2026-08-09 구현) — recentTrade가 지분(구분소유 일부) 거래일 때도
// "현재가"로 그대로 쓰여 총투자금·ROI가 왜곡되는 버그 수정. 실측 사례: 연면적 23,658㎡ 건물에 3.77㎡
// 호실 3,000만원짜리 거래가 매칭돼, 그게 그대로 "매입가"로 들어가 미래가치가 1,606억으로 계산됨.
// 판정 규칙: 거래 면적이 대상 면적의 절반(REPRESENTATIVE_AREA_RATIO) 미만이면 "이 건물 전체의 대표
// 가격이 아니다"로 보고 estimatedPrice로 대체한다 — F-08이 이미 "면적 ±10~20% 이내만 유사거래로
// 인정"하는 원칙을 쓰고 있어(MarketServiceImpl STAGE_AREA_RATIO/WIDENED_AREA_RATIO), 그보다 훨씬(50%)
// 벗어난 단일 거래는 같은 논리의 연장.
// F-09(현재가→ROI, InvestmentServiceImpl)와 F-10 §8.17(시장 내 가격 위치)이 이 판정을 공유해야 두
// 화면의 "이 매물의 현재가"가 서로 어긋나지 않는다 — 판정 로직을 여기 한 곳에서만 관리.
public final class RepresentativePriceCalculator {

    private static final BigDecimal REPRESENTATIVE_AREA_RATIO = BigDecimal.valueOf(0.5);

    private RepresentativePriceCalculator() {
    }

    // recentTrade가 지분거래로 판정되거나 아예 없으면 estimatedPrice로 대체한다. estimatedPrice마저
    // UNAVAILABLE이면 null(호출부가 "현재가 산출 불가"로 처리 — 0인 것과 없는 것은 다르다는 관례 그대로).
    public static BigDecimal representativePriceOrNull(RecentTradeResponse recentTrade, EstimatedPriceResponse estimatedPrice,
                                                         BigDecimal targetArea) {
        if (recentTrade != null && isRepresentative(recentTrade, targetArea)) {
            return recentTrade.price();
        }
        return estimatedPrice.confidenceLevel() == ConfidenceLevel.UNAVAILABLE ? null : estimatedPrice.value();
    }

    // §8.17 "시장 내 가격 위치" thisPropertyPercentile용 — "이 매물의 ㎡당가"도 같은 판정을 재사용한다.
    // recentTrade가 대표성 있으면 그 거래 자체의 ㎡당가(price÷area), 아니면 이미 계산된 중앙값
    // (comparableTrades 모집단의 medianPricePerSqm)을 그대로 쓴다 — 후자의 경우 정의상 항상 정확히
    // 중앙값(percentile 50)이 된다.
    public static BigDecimal representativePricePerSqm(RecentTradeResponse recentTrade, BigDecimal medianPricePerSqm,
                                                         BigDecimal targetArea) {
        if (recentTrade != null && isRepresentative(recentTrade, targetArea)) {
            return recentTrade.price().divide(recentTrade.area(), 10, RoundingMode.HALF_UP);
        }
        return medianPricePerSqm;
    }

    // 거래 면적/대상 면적을 비교할 수 없으면(둘 중 하나라도 없음) 안전하게 "대표성 없음"으로 간주해
    // estimatedPrice로 대체한다 — 근거 없이 recentTrade를 그대로 쓰지 않는다(`DOMAIN.md` §4).
    private static boolean isRepresentative(RecentTradeResponse recentTrade, BigDecimal targetArea) {
        if (recentTrade.area() == null || targetArea == null || targetArea.signum() <= 0) {
            return false;
        }
        return recentTrade.area().compareTo(targetArea.multiply(REPRESENTATIVE_AREA_RATIO)) >= 0;
    }
}
