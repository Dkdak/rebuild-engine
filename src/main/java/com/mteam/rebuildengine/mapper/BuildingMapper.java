package com.mteam.rebuildengine.mapper;

import com.mteam.rebuildengine.model.read.BuildingReadModel;
import com.mteam.rebuildengine.model.read.GradeSummaryReadModel;
import com.mteam.rebuildengine.model.read.PropertySearchReadModel;

import java.util.List;

public interface BuildingMapper {
    List<BuildingReadModel> searchByDong(BuildingDongSearchCondition condition);

    long countByDong(BuildingDongSearchCondition condition);

    // gis_building(좌표)·trade(최근 거래)를 LEFT JOIN으로 흡수한 결과 — 별도 배치 조회 없이 한 쿼리로 받는다.
    List<PropertySearchReadModel> searchForPropertySearch(BuildingPropertySearchCondition condition);

    long countForPropertySearch(BuildingPropertySearchCondition condition);

    // condition.grade는 무시된다(집계 대상이 grade 자체라 필터링하면 안 됨) — 호출부가 grade=null로 넘긴다.
    List<GradeSummaryReadModel> gradeSummaryForPropertySearch(BuildingPropertySearchCondition condition);
}
