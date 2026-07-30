package com.mteam.rebuildengine.mapper;

import com.mteam.rebuildengine.model.read.BuildingReadModel;
import com.mteam.rebuildengine.model.read.GradeSummaryReadModel;

import java.util.List;

public interface BuildingMapper {
    List<BuildingReadModel> searchByDong(BuildingDongSearchCondition condition);

    long countByDong(BuildingDongSearchCondition condition);

    List<BuildingReadModel> searchForPropertySearch(BuildingPropertySearchCondition condition);

    long countForPropertySearch(BuildingPropertySearchCondition condition);

    // condition.grade는 무시된다(집계 대상이 grade 자체라 필터링하면 안 됨) — 호출부가 grade=null로 넘긴다.
    List<GradeSummaryReadModel> gradeSummaryForPropertySearch(BuildingPropertySearchCondition condition);
}
