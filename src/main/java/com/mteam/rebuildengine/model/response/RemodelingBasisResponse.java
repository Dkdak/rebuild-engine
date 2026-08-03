package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// F-06 §2.1 "판단 근거 목록" / docs/law/LAW-001 §5. buildingAgeYears가 null이면 사용승인일 데이터
// 없음(§3.4 "산출 불가"). zoneName 이하는 landuse 매칭 안 되면 전부 null("정보 준비 중").
// districtNames는 지구단위계획구역 등 중첩 지정 — 스코어링엔 안 들어감(표시 전용), 지정이 없으면
// 빈 리스트(landuse_district에 building_id 매칭 자체가 없으면 "정보 준비 중"과 구분 안 됨, §5.1 참고).
public record RemodelingBasisResponse(
        Integer buildingAgeYears,
        boolean gatePassed,
        Integer gateYears,
        Integer requiredYears,
        String zoneName,
        List<String> districtNames,
        BigDecimal floorAreaRatioLimit,
        BigDecimal floorAreaRatioSurplus,
        BigDecimal additionalBuildableAreaSqm,
        Integer estimatedAdditionalHouseholds,
        String recentPermitType,
        LocalDate recentPermitDate,
        boolean permitInProgress
) {
}
