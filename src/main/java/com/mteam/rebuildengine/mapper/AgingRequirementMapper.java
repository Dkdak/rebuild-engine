package com.mteam.rebuildengine.mapper;

import com.mteam.rebuildengine.model.read.AgingRequirementReadModel;

import java.util.Optional;

// docs/law/LAW-001 §2/§3 — 노후도 판정 참조 테이블(aging_requirement) 조회. flat(연도·층수 무관)
// 3종과 curve(공동주택+RC계열, 별표1) 1종을 나눠 조회한다(seed_aging_requirement.sql 참고).
public interface AgingRequirementMapper {
    Optional<AgingRequirementReadModel> findFlat(String housingType, String structureGroup);

    Optional<AgingRequirementReadModel> findCurve(String housingType, String structureGroup, String floorTier,
                                                   int approvalYear);
}
