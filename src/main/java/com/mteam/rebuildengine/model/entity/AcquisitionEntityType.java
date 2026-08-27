package com.mteam.rebuildengine.model.entity;

// F-19 STEP4 "취득 주체"(FEATURE_19_PERSONALIZED_ANALYSIS.md §3.3) — LAW-003 취득세 요율 확장의
// 분기 기준. 개인 1주택은 기존 표준세율(5.5%), 다주택·법인은 중과세율 대상.
public enum AcquisitionEntityType {
    INDIVIDUAL_SINGLE_HOME,
    INDIVIDUAL_MULTI_HOME,
    CORPORATION
}
