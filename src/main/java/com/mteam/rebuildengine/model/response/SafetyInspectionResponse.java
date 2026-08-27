package com.mteam.rebuildengine.model.response;

import java.time.LocalDate;

// F-19 STEP1 안전진단(FEATURE_19_PERSONALIZED_ANALYSIS.md §2.2-d) — measurement.safety_inspection
// jsonb의 직렬화 형태. LAW-004 확정 전까지 등급 체계·허용 증축 방식 값을 코드로 검증하지 않는다
// (그대로 저장·표시만) — 그래서 grade/allowedExpansionType은 자유 문자열이다.
public record SafetyInspectionResponse(String grade, LocalDate inspectionDate, String allowedExpansionType) {
}
