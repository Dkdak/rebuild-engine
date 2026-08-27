package com.mteam.rebuildengine.model.response;

// F-19 진행도(FEATURE_19_PERSONALIZED_ANALYSIS.md §2.3-b) — "개수가 아니라 ROI가 무엇에 기대고
// 있나". 14개 항목 중 ROI를 직접 만드는 4개(증축면적/견적/매입가/예상시세)만 센다.
public record MeasurementProgressResponse(int measured, int total) {
}
