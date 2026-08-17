package com.mteam.rebuildengine.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

// LAW-003_취득세_중개보수_요율.md — 총 투자금에 포함되는 부대비용(취득세+중개보수, 만원 단위) 추정치.
// InvestmentServiceImpl(F-09 배치 ROI)과 프론트 calcAcquisitionCost(analysisApi.ts)가 같은 공식을
// 각자 구현한다 — 두 값이 서로 어긋나지 않으려면 이 상수·구간이 그쪽과 항상 같아야 한다(2026-08-17
// "01 요약정보/06 사업성분석 ROI 불일치" 버그의 재발 방지 포인트).
public final class AcquisitionCostCalculator {

    private static final BigDecimal HOUSING_TAX_LOW_THRESHOLD = BigDecimal.valueOf(60_000); // 6억(만원)
    private static final BigDecimal HOUSING_TAX_HIGH_THRESHOLD = BigDecimal.valueOf(90_000); // 9억(만원)
    private static final BigDecimal HOUSING_TAX_RATE_LOW = BigDecimal.valueOf(0.01);
    private static final BigDecimal HOUSING_TAX_RATE_HIGH = BigDecimal.valueOf(0.03);
    private static final BigDecimal HOUSING_TAX_BRACKET_WIDTH = BigDecimal.valueOf(30_000); // 6~9억 구간 폭
    private static final BigDecimal HOUSING_TAX_BRACKET_SLOPE = BigDecimal.valueOf(0.02);

    // 주택 외 — 취득세4%+지방교육세0.4%+농특세0.2%+중개보수0.9%=5.5%(LAW-003 §1~4, 전부 확정치).
    private static final BigDecimal NON_HOUSING_RATE = BigDecimal.valueOf(0.055);

    private static final int CALC_SCALE = 10;

    private AcquisitionCostCalculator() {
    }

    // baseValue(매입가, 만원 단위) 기준 부대비용 추정치. 주택은 취득세+중개보수만(지방교육세·농특세는
    // LAW-003 §5 Open Item, 공식 미확정이라 제외) — 주택 외는 4개 항목 합계 5.5% 고정.
    public static BigDecimal calculate(BigDecimal baseValue, boolean isHousing) {
        if (isHousing) {
            BigDecimal taxRate = housingTaxRate(baseValue);
            BigDecimal brokerageRate = housingBrokerageRate(baseValue);
            return baseValue.multiply(taxRate).add(baseValue.multiply(brokerageRate));
        }
        return baseValue.multiply(NON_HOUSING_RATE);
    }

    // 6억 이하 1% / 9억 초과 3% / 6~9억 구간 선형 근사(LAW-003 §1, 법정 산식 원문 미확인 근사치, 경계값만 확정).
    private static BigDecimal housingTaxRate(BigDecimal baseValue) {
        if (baseValue.compareTo(HOUSING_TAX_LOW_THRESHOLD) <= 0) {
            return HOUSING_TAX_RATE_LOW;
        }
        if (baseValue.compareTo(HOUSING_TAX_HIGH_THRESHOLD) > 0) {
            return HOUSING_TAX_RATE_HIGH;
        }
        BigDecimal excess = baseValue.subtract(HOUSING_TAX_LOW_THRESHOLD);
        return HOUSING_TAX_RATE_LOW.add(excess
                .divide(HOUSING_TAX_BRACKET_WIDTH, CALC_SCALE, RoundingMode.HALF_UP)
                .multiply(HOUSING_TAX_BRACKET_SLOPE));
    }

    // 구간별 고정 요율(LAW-003 §4). 한도액(5천만원/2억 구간)은 V1 미반영 — 근사치.
    private static BigDecimal housingBrokerageRate(BigDecimal baseValue) {
        if (baseValue.compareTo(BigDecimal.valueOf(5_000)) < 0) {
            return BigDecimal.valueOf(0.006);
        }
        if (baseValue.compareTo(BigDecimal.valueOf(20_000)) < 0) {
            return BigDecimal.valueOf(0.005);
        }
        if (baseValue.compareTo(BigDecimal.valueOf(90_000)) < 0) {
            return BigDecimal.valueOf(0.004);
        }
        if (baseValue.compareTo(BigDecimal.valueOf(120_000)) < 0) {
            return BigDecimal.valueOf(0.005);
        }
        if (baseValue.compareTo(BigDecimal.valueOf(150_000)) < 0) {
            return BigDecimal.valueOf(0.006);
        }
        return BigDecimal.valueOf(0.007);
    }
}
