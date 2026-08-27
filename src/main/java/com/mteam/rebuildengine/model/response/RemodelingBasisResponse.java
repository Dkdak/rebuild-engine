package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// F-06 §2.1 "판단 근거 목록" / docs/law/LAW-001 §5. buildingAgeYears가 null이면 사용승인일 데이터
// 없음(§3.4 "산출 불가"). zoneName 이하는 landuse 매칭 안 되면 전부 null("정보 준비 중").
// districtNames는 지구단위계획구역 등 중첩 지정 — 스코어링엔 안 들어감(표시 전용), 지정이 없으면
// 빈 리스트(landuse_district에 building_id 매칭 자체가 없으면 "정보 준비 중"과 구분 안 됨, §5.1 참고).
// landAreaSqm/grossFloorAreaSqm(FEATURE_19 §2.2-f, 2026-08-27 추가) — F-19 STEP2 참고 영역용. 건축물
// 대장 원본값 그대로(BuildingEntity.siar/gfa, additionalBuildableAreaSqm 계산에 이미 쓰던 값들이라
// landuse 매칭 여부와 무관하게 항상 채워진다) — zoneName 이하와 달리 "정보 준비 중"이 없다.
// currentFloorAreaRatio/farComputationGfa(§2.2-f, 2026-08-27 추가) — 화면 표시 전용, 계산엔 안 쓴다.
// currentFloorAreaRatio는 대장이 이미 계산해 내려주는 현재 용적률(%, BuildingEntity.fart) 그대로라
// floorAreaRatioLimit − floorAreaRatioSurplus로 역산할 필요가 없다(지구단위계획 오버라이드로 상한이
// 바뀌어도 이 값 자체는 항상 정확). farComputationGfa(BuildingEntity.fartCmpttnGfa, 용적률산정연면적)는
// grossFloorAreaSqm(대장 연면적)과 다른 이유(지하·주차 등 제외)를 화면에서 설명하는 용도.
public record RemodelingBasisResponse(
        Integer buildingAgeYears,
        boolean gatePassed,
        Integer gateYears,
        Integer requiredYears,
        String zoneName,
        List<String> districtNames,
        BigDecimal floorAreaRatioLimit,
        BigDecimal floorAreaRatioSurplus,
        BigDecimal currentFloorAreaRatio,
        BigDecimal landAreaSqm,
        BigDecimal grossFloorAreaSqm,
        BigDecimal farComputationGfa,
        BigDecimal additionalBuildableAreaSqm,
        Integer estimatedAdditionalHouseholds,
        String recentPermitType,
        LocalDate recentPermitDate,
        boolean permitInProgress
) {
}
