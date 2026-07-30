package com.mteam.rebuildengine.mapper;

// F-05 §3.1 법정동 단위 조회(buildings/title) 조건. limit/offset은 pageNo/numOfRows를 서비스에서 변환한 값.
public record BuildingDongSearchCondition(
        String sggNm,
        String bjdongNm,
        String platGbCd,
        String bun,
        String ji,
        int limit,
        int offset
) {
}
