package com.mteam.rebuildengine.model.response;

import com.mteam.rebuildengine.model.entity.BuildingEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

// 건축물대장 표제부 기본 물리속성 + 좌표(F-14 매핑 결과, 매칭 실패 시 null). FEATURE_05 §3.1.
public record BuildingInfoResponse(
        String bdrgSn,
        String platPlc,
        String sggCdNm,
        String stdgCdNm,
        BigDecimal siteArea,
        BigDecimal archArea,
        BigDecimal buildingCoverageRatio,
        BigDecimal grossFloorArea,
        BigDecimal floorAreaRatio,
        String structureNm,
        String mainUsageNm,
        Integer groundFloors,
        Integer undergroundFloors,
        Integer householdCount,
        LocalDate useApprovalDate,
        BigDecimal lat,
        BigDecimal lng
) {
    public static BuildingInfoResponse of(BuildingEntity building, BigDecimal lat, BigDecimal lng) {
        return new BuildingInfoResponse(
                building.getBdrgSn(),
                building.getPlatPlc(),
                building.getSggCdNm(),
                building.getStdgCdNm(),
                building.getSiar(),
                building.getBdar(),
                building.getBdcvrt(),
                building.getGfa(),
                building.getFart(),
                building.getStrctCdNm(),
                building.getMnUsgCdNm(),
                building.getGrndNofl(),
                building.getUdgdNofl(),
                building.getHhCnt(),
                building.getUseAprvYmd(),
                lat,
                lng
        );
    }
}
