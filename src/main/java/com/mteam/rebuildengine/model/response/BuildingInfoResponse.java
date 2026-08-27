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
// roofNm~parkingCount(2026-08-08 추가): 전부 BuildingEntity(건축물대장 표제부)에 이미 있던 값 —
// 새 계산·새 데이터 소스 없음. elevatorCount(승용+비상용)·parkingCount(옥내+옥외 기계식+자주식 4종
// 합산)는 세부 구분 없이 "대수"로만 노출하는 UI 요구라 서비스 레이어에서 더해 하나의 필드로 내려준다.
// seismicDesign*·auxiliaryBuilding*는 서로 단위가 달라(여부/능력값, 동수/면적) 합산 대상이 아니라
// 원본 컬럼 그대로 각각 노출한다. 내부마감/외부마감/대수선이력은 표제부 원본에 없는 컬럼이라 이번에
// 추가하지 않았다 — 필요하면 별도 데이터 소스 조사부터 필요.
// sitePolygon(2026-08-08 추가): gis_building.polygonGeojson을 building_gis_mapping(F-14)으로 조인해
// 그대로 반환 — lat/lng와 같은 GisBuildingEntity에서 함께 꺼내 쓰므로 추가 쿼리 없음, 매칭 실패 시 null.
// siteBoundaryPolygon(2026-08-09 추가) — sitePolygon과 다른 폴리곤이다(sitePolygon=건물 외곽선,
// siteBoundaryPolygon=대지/필지 경계, §5.1 명칭 정정 참고). gis_building.pnu로 site_boundary(연속지적도,
// 신규 F-14류 파이프라인)를 한 번 더 조인 — pnu 매칭 실패 시 null(실측 매칭률 99.87%).
// coverageRatioLimit(2026-08-08 추가): F-06 RemodelingServiceImpl의 floorAreaRatioLimit(용적률 법정상한)
// 산출과 같은 조인(landuse.zoneName → zoning_limit)을 재사용해 그 짝인 건폐율 법정상한을 노출한다 —
// landuse 미매칭이거나 zoneName이 zoning_limit에 없으면 null(zoneName 자체는 F-06 응답에만 있고 이
// DTO엔 없음, floorAreaRatioLimit과 동일하게 값만 가져옴).
// farComputationGfa(2026-08-27 추가, product 리포트 02 카드 확인) — grossFloorArea(대장 연면적)와
// floorAreaRatio(용적률산정연면적 기준으로 대장이 이미 계산해 내려주는 값)가 서로 다른 면적 기준이라
// 같은 카드에 나란히 두면 "158÷96≠112.57%"처럼 안 맞아 보인다. 계산엔 안 쓰고 표시 전용(대장 원본값
// 그대로, F-06 basis.farComputationGfa와 같은 성격) — 프론트가 "산정 108㎡ (대장 158㎡)"로 병기하는 데 씀.
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
        BigDecimal farComputationGfa,
        String structureNm,
        String mainUsageNm,
        Integer groundFloors,
        Integer undergroundFloors,
        Integer householdCount,
        LocalDate useApprovalDate,
        BigDecimal lat,
        BigDecimal lng,
        RecentTradeResponse recentTrade,
        String roofNm,
        Integer elevatorCount,
        String seismicDesignYn,
        String seismicCapacity,
        Integer auxiliaryBuildingCount,
        BigDecimal auxiliaryBuildingArea,
        Integer parkingCount,
        String sitePolygon,
        BigDecimal coverageRatioLimit,
        String siteBoundaryPolygon
) {
    public static BuildingInfoResponse of(BuildingEntity building, BigDecimal lat, BigDecimal lng, TradeEntity recentTrade,
                                           String sitePolygon, BigDecimal coverageRatioLimit, String siteBoundaryPolygon) {
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
                building.getFartCmpttnGfa(),
                building.getStrctCdNm(),
                building.getMnUsgCdNm(),
                building.getGrndNofl(),
                building.getUdgdNofl(),
                building.getHhCnt(),
                building.getUseAprvYmd(),
                lat,
                lng,
                recentTrade != null ? RecentTradeResponse.from(recentTrade) : null,
                building.getRoofCdNm(),
                sumOrNull(building.getPsngrElvtrCnt(), building.getEuseElvtrCnt()),
                building.getRserDesignAplcnYn(),
                building.getRserAbltCn(),
                building.getAnxBdstCnt(),
                building.getAnxBdstArea(),
                sumOrNull(building.getIndrMcnclCntom(), building.getOtdrMcnclCntom(),
                        building.getIndrSfprplCntom(), building.getOtdrSfprplCntom()),
                sitePolygon,
                coverageRatioLimit,
                siteBoundaryPolygon
        );
    }

    public static BuildingInfoResponse of(BuildingReadModel building, BigDecimal lat, BigDecimal lng, TradeEntity recentTrade) {
        return of(building, lat, lng, recentTrade != null ? RecentTradeResponse.from(recentTrade) : null);
    }

    // searchForPropertySearch(F-04)는 trade를 LATERAL JOIN으로 이미 흡수해 TradeEntity가 아니라 값 그대로
    // 온다 — 별도 JPA 조회 없이 이 오버로드로 바로 만든다.
    public static BuildingInfoResponse of(BuildingReadModel building, BigDecimal lat, BigDecimal lng, RecentTradeResponse recentTrade) {
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
                null, // farComputationGfa — BuildingReadModel(F-04 목록용 슬림 프로젝션)엔 이 컬럼이 없다
                building.strctCdNm(),
                building.mnUsgCdNm(),
                building.grndNofl(),
                building.udgdNofl(),
                building.hhCnt(),
                building.useAprvYmd(),
                lat,
                lng,
                recentTrade,
                // BuildingReadModel은 동 단위 목록 검색(F-04)용 슬림 프로젝션이라 표제부 상세 컬럼을
                // 애초에 안 가져온다 — 목록 화면엔 필요 없는 값들이라 null로 둔다(상세 조회는 findByBdrgSn,
                // BuildingEntity 경로만 탄다). sitePolygon·coverageRatioLimit·siteBoundaryPolygon도 같은 이유로 null.
                null, null, null, null, null, null, null, null, null, null
        );
    }

    private static BigDecimal nullIfZero(BigDecimal value) {
        return value == null || value.signum() == 0 ? null : value;
    }

    // 전부 null이면(데이터 없음) null 유지, 하나라도 있으면 나머지는 0으로 취급해 합산.
    private static Integer sumOrNull(Integer... values) {
        boolean allNull = true;
        int sum = 0;
        for (Integer value : values) {
            if (value != null) {
                allNull = false;
                sum += value;
            }
        }
        return allNull ? null : sum;
    }
}
