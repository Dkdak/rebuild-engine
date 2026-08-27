package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// F-19 GET .../history(§3.2) — 값이 실제로 바뀐 저장만 존재한다(§2.2-b, 확인만 하고 같은 값 저장은
// 이력에 안 남음). previousValue/newValue는 저장된 jsonb를 그대로 문자열로 내려준다 — 항목마다
// 모양이 달라 프론트가 itemKey로 분기해 렌더링한다.
public record MeasurementHistoryEntryResponse(
        LocalDateTime changedAt, int stepNo, String itemKey,
        String previousValue, String newValue,
        BigDecimal measuredRoiAtChange
) {
}
