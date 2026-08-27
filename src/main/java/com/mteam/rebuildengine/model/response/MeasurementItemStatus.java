package com.mteam.rebuildengine.model.response;

// F-19 항목 상태 3종(DOMAIN.md §7.5, FEATURE_19_PERSONALIZED_ANALYSIS.md §2.2-b) — 별도 플래그 컬럼
// 없이 서비스 레이어가 시각 비교로 매 조회 시 판정한다("확인중" 상태는 두지 않는다, §2.2-b).
public enum MeasurementItemStatus {
    ESTIMATED,  // 아직 입력 안 함 — 공공데이터 추정치 그대로
    MEASURED,   // 실측 입력됨, 아직 낡지 않음
    RECHECK     // 의존 변경 또는 유효기간 경과로 재확인 필요
}
