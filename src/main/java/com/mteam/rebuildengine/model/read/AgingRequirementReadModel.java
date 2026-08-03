package com.mteam.rebuildengine.model.read;

// FEATURE_06_REMODELING.md §3.2-1, docs/law/LAW-001 — 리모델링 허용연한(gateYears)·노후불량건축물
// 연한(requiredYears) 조회 결과.
public record AgingRequirementReadModel(int gateYears, int requiredYears) {
}
