package com.mteam.rebuildengine.service.favorite;

import com.mteam.rebuildengine.model.response.FavoriteListResponse;

import java.util.List;

public interface FavoriteService {
    // FEATURE_11_FAVORITES.md §3.2 POST /api/v1/favorites — 등록 시점 등급·ROI는 서버가 investment_result에서
    // 읽어 저장한다(프론트가 보내지 않음). 이미 담긴 매물이면 재등록(reactivate)으로 처리.
    void add(String email, String buildingId);

    // DELETE /api/v1/favorites/{buildingId} — 소프트 삭제. 이미 해제됐거나 애초에 없으면 조용히 무시(멱등).
    void remove(String email, String buildingId);

    FavoriteListResponse list(String email, int page, int size);

    // GET /api/v1/favorites/ids — ♥ 표시용 buildingId 목록(§2.3).
    List<String> listIds(String email);
}
