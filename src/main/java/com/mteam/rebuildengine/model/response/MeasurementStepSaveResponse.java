package com.mteam.rebuildengine.model.response;

import java.util.List;

// F-19 PUT .../steps/{stepNo} 응답(§3.2) — 재계산 결과 + 단계별(항목별) 상태 + 진행도 + 이 저장으로
// 재확인이 새로 붙은 항목 목록.
public record MeasurementStepSaveResponse(
        MeasurementRecalculationResponse recalculation,
        List<MeasurementItemStatusResponse> itemStatuses,
        List<String> recheckTriggeredItemKeys,
        MeasurementProgressResponse progress
) {
}
