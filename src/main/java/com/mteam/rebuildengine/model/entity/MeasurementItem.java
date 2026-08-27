package com.mteam.rebuildengine.model.entity;

// F-19 실측 입력 14개 항목(FEATURE_19_PERSONALIZED_ANALYSIS.md §2.3-b가 단일 출처) — measurement_validity.
// item_key, measurement_history.item_key와 이름이 1:1로 대응한다. stepNo는 §2.2 의존 순서 5단계.
public enum MeasurementItem {
    ZONING(1),
    HEIGHT_LIMIT(1),
    SAFETY(1),
    EXPANDABLE_AREA(2),
    REDUCTION_REASON(2),
    ESTIMATE(3),
    ESTIMATE_BASIS(3),
    DESIGN_PERMIT_FEE(3),
    PURCHASE_PRICE(4),
    ACQUISITION_ENTITY(4),
    REGISTRY_RIGHTS(4),
    LEASE_VACANCY(4),
    FUTURE_VALUE(5),
    FUTURE_VALUE_BASIS(5);

    private final int stepNo;

    MeasurementItem(int stepNo) {
        this.stepNo = stepNo;
    }

    public int stepNo() {
        return stepNo;
    }
}
