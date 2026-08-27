package com.mteam.rebuildengine.model.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

// F-19 항목별 상태 한 줄 — itemKey는 MeasurementItem enum 이름 그대로(예: "EXPANDABLE_AREA").
// anchorUsed(§3.1-a) — 유효기간 판정에 실제로 쓰인 기준일 종류: "INPUT_AT"(입력 시각) 또는
// "DOCUMENT_DATE"(서류 날짜). 서류 날짜를 지원하는 항목이라도 값을 안 넣었으면 INPUT_AT으로 폴백된
// 실제 판정 결과를 그대로 반영한다 — 프론트가 "값이 비었는지"로 문구를 추측하지 않게 서버가 확정해 준다.
// anchorDate/elapsedDays(2026-08-24 추가, §2.3-c "경과일·재확인 판정은 프론트에서 계산하지 않는다") —
// anchorUsed가 가리키는 실제 날짜와 그날부터의 경과일. "견적서 발행일 2026-07-02 · 53일 경과" 같은
// 문구를 프론트가 직접 계산 없이 그대로 조립할 수 있게 한다.
public record MeasurementItemStatusResponse(String itemKey, int stepNo, MeasurementItemStatus status,
                                             LocalDateTime inputAt, String anchorUsed,
                                             LocalDate anchorDate, Long elapsedDays) {
}
