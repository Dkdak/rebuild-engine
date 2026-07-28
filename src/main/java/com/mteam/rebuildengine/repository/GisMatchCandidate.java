package com.mteam.rebuildengine.repository;

import java.math.BigDecimal;

// building_gis_mapping 매칭 배치 전용 경량 프로젝션 — gis_building 전체(695K건)를 메모리에 올릴 때
// polygon_geojson 등 매칭에 안 쓰는 무거운 컬럼은 제외한다.
public record GisMatchCandidate(Long id, String pnu, String bjdongCd, String mnLotno, String subLotno,
                                 BigDecimal totalFloorArea, BigDecimal archArea, String mainPurposeNm,
                                 String buildingUfid, Integer partNo) {
}
