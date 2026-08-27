package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;

// F-19 §3.2-a GET /api/v1/analysis/measurements — F-03 대시보드 4개 자리가 이 하나를 소비한다.
// 미시작(실측 레코드 자체가 없음)은 이 목록에 안 나온다 — 프론트가 F-11 관심목록과 buildingId로
// 매칭해 3분류(미시작/진행중/완료)를 만든다.
// recheckCount(2026-08-24 추가, frontend 요청) — 14개 항목 전체 기준(ROI 핵심 4개만이 아님). "완료"
// 라도 STEP1(규제·안전진단) 같은 부속 항목이 유효기간을 넘기면 확인할 게 남은 것이라 진행도(4개
// 기준)와는 별도로 세야 한다 — "재확인 n"이 "완료"보다 우선 표시되는 이유(product 규칙)와 맞춘 것.
public record MeasurementListItemResponse(
        String buildingId, String address,
        MeasurementProgressResponse progress,
        String status,       // COMPLETED / IN_PROGRESS
        BigDecimal measuredRoi,
        String nextInputField,
        int recheckCount
) {
}
