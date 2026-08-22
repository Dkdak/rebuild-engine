package com.mteam.rebuildengine.model.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// FEATURE_11_FAVORITES.md §3.2 GET /api/v1/favorites — 매물 정보는 F-04 PropertyResponse를 그대로
// 재사용한다(신규 카드 스키마를 만들지 않음), gradeAtSave/roiAtSave/savedAt만 덧붙인다. property가
// null이면 배치에서 소프트 삭제된 건물(멸실·재건축 등) — 목록에서 지우지 않고 프론트가 "더 이상 조회할
// 수 없는 건물"로 표시한다(§4). buildingId는 property가 null이어도 해제(DELETE) 액션이 되도록 top-level 유지.
public record FavoriteListResponse(
        List<FavoriteItem> items,
        long totalCount,
        int page,
        int size,
        int totalPages
) {
    public record FavoriteItem(String buildingId, PropertyResponse property,
                                String gradeAtSave, BigDecimal roiAtSave, LocalDateTime savedAt) {
    }
}
