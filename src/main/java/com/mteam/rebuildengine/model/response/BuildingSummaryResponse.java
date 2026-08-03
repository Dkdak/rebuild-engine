package com.mteam.rebuildengine.model.response;

// F-05 §2.1 "단지 정보" 카드(신규, 2026-08-08) — 공동주택 매물에만 노출 대상(프론트 판단), API는
// 유형 무관하게 조회 결과 그대로 반환. building_summary 매칭이 없으면 전부 null("정보 없음").
public record BuildingSummaryResponse(
        Integer householdCount,
        Integer mainBuildingCount,
        Integer elevatorPassengerCount,
        Integer elevatorEmergencyCount
) {
    public static BuildingSummaryResponse empty() {
        return new BuildingSummaryResponse(null, null, null, null);
    }
}
