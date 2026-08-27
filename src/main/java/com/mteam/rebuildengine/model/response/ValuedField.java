package com.mteam.rebuildengine.model.response;

// F-10(BuildingReportResponse)·F-19(MeasurementDetailResponse) 공용 — "값 + 그 값이 실측인지"를
// 함께 내려주는 표준 모양. measured=false여도 value는 채워진다(공공데이터 추정치) — §2.2-b 규칙 2
// "화면을 열면 전 항목이 리포트 추정치로 이미 채워져 있다. 빈 양식이 아니다"를 만족하려면 실측이
// 없다고 값 자체를 비우면 안 된다.
public record ValuedField<T>(T value, boolean measured) {
}
