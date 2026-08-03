package com.mteam.rebuildengine.model.response;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.model.entity.TradeEntity;
import com.mteam.rebuildengine.model.read.BuildingReadModel;

import java.math.BigDecimal;
import java.time.LocalDate;

// 건축물대장 표제부 기본 물리속성 + 좌표(F-14 매핑 결과, 매칭 실패 시 null). FEATURE_05 §3.1.
// recentTrade(F-04 §2.1-h, F-05 §5.2)는 trade.building_id 매핑(F-15 §3.4) 결과 — 매칭 안 되면 null.
// siteArea/buildingCoverageRatio/floorAreaRatio는 원본(BuildingEntity.siar/bdcvrt/fart)에 데이터
// 미확보 시 0으로 적재돼 있다(F-12/F-13 원본 특성) — 실제 건물은 이 세 값이 0일 수 없어 0은 항상
// "데이터 없음"을 의미한다. lat/lng/recentTrade와 같은 "없으면 null" 컨벤션(DOMAIN.md §7.2)에 맞춰
// 여기서 0을 null로 정규화한다(2026-08-08, 프론트 확인으로 발견).
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
        BigDecimal lng,
        RecentTradeResponse recentTrade
) {
    public static BuildingInfoResponse of(BuildingEntity building, BigDecimal lat, BigDecimal lng, TradeEntity recentTrade) {
        return new BuildingInfoResponse(
                building.getBdrgSn(),
                building.getPlatPlc(),
                building.getSggCdNm(),
                building.getStdgCdNm(),
                nullIfZero(building.getSiar()),
                building.getBdar(),
                nullIfZero(building.getBdcvrt()),
                building.getGfa(),
                nullIfZero(building.getFart()),
                building.getStrctCdNm(),
                building.getMnUsgCdNm(),
                building.getGrndNofl(),
                building.getUdgdNofl(),
                building.getHhCnt(),
                building.getUseAprvYmd(),
                lat,
                lng,
                recentTrade != null ? RecentTradeResponse.from(recentTrade) : null
        );
    }

    public static BuildingInfoResponse of(BuildingReadModel building, BigDecimal lat, BigDecimal lng, TradeEntity recentTrade) {
        return new BuildingInfoResponse(
                building.bdrgSn(),
                building.platPlc(),
                building.sggCdNm(),
                building.stdgCdNm(),
                nullIfZero(building.siar()),
                building.bdar(),
                nullIfZero(building.bdcvrt()),
                building.gfa(),
                nullIfZero(building.fart()),
                building.strctCdNm(),
                building.mnUsgCdNm(),
                building.grndNofl(),
                building.udgdNofl(),
                building.hhCnt(),
                building.useAprvYmd(),
                lat,
                lng,
                recentTrade != null ? RecentTradeResponse.from(recentTrade) : null
        );
    }

    private static BigDecimal nullIfZero(BigDecimal value) {
        return value == null || value.signum() == 0 ? null : value;
    }
}
