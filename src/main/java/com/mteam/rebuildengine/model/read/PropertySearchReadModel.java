package com.mteam.rebuildengine.model.read;

import java.math.BigDecimal;
import java.time.LocalDate;

// F-04 §3.1 searchForPropertySearch 전용 — building에 gis_building(좌표, building_gis_mapping 경유)을
// LEFT JOIN으로 흡수한 결과. BuildingReadModel(searchByDong 등 다른 조회가 공유하는 슬림 모델)과는
// 분리해서 이 조인 전용 필드만 여기 둔다 — 매칭 실패 시 lat/lng는 null(LEFT JOIN 특성 그대로).
public record PropertySearchReadModel(
        String bdrgSn,
        String platPlc,
        String sggCdNm,
        String stdgCdNm,
        BigDecimal siar,
        BigDecimal bdar,
        BigDecimal bdcvrt,
        BigDecimal gfa,
        BigDecimal fart,
        String strctCdNm,
        String mnUsgCdNm,
        Integer grndNofl,
        Integer udgdNofl,
        Integer hhCnt,
        LocalDate useAprvYmd,
        BigDecimal lat,
        BigDecimal lng
) {
    public BuildingReadModel toBuildingReadModel() {
        return new BuildingReadModel(bdrgSn, platPlc, sggCdNm, stdgCdNm, siar, bdar, bdcvrt, gfa, fart,
                strctCdNm, mnUsgCdNm, grndNofl, udgdNofl, hhCnt, useAprvYmd);
    }
}
