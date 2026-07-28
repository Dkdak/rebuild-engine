package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// HELP6 §3 Custom Repository 패턴 — 조건이 많은 검색은 Query Method/긴 @Query 대신 비즈니스 의미가
// 드러나는 메서드로 노출하고, 구현(Criteria API)은 BuildingRepositoryImpl에 둔다.
public interface BuildingRepositoryCustom {
    Page<BuildingEntity> search(BuildingSearchCriteria criteria, Pageable pageable);
}
