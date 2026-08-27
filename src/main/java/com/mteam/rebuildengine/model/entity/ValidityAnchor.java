package com.mteam.rebuildengine.model.entity;

// F-19 measurement_validity §3.1-a(2026-08-24 추가) — 유효기간 기준일. 대부분 항목은 입력 시각부터
// 재지만(INPUT_AT), 서류가 있는 3개 항목(안전진단·실제 견적·증축 가능 연면적)은 서류 자체의 날짜부터
// 잰다(DOCUMENT_DATE) — 오래된 서류를 오늘 입력해도 유효기간이 오늘부터 새로 시작되면 안 되기 때문.
public enum ValidityAnchor {
    INPUT_AT,
    DOCUMENT_DATE
}
