package com.mteam.rebuildengine.model.response;

import com.mteam.rebuildengine.model.read.SearchIndexReadModel;

import java.math.BigDecimal;

// FEATURE_04 §3.1 GET /api/v1/search-index/search 응답 항목. BUILDING만 buildingId/lat/lng 값이 있다.
// GU 타입(2026-08-03 추가)은 bjdongCd 필드에 legal_dong_code.sigungu_cd(5자리)를 담아 재사용한다 —
// properties/search 호출 시 그대로 sigunguCd 파라미터로 전달하면 구 전체 범위로 조회된다(§1.2).
public record SearchIndexCandidateResponse(
        String type,
        String buildingId,
        String bjdongCd,
        String displayText,
        BigDecimal lat,
        BigDecimal lng
) {
    public static SearchIndexCandidateResponse from(SearchIndexReadModel readModel) {
        return new SearchIndexCandidateResponse(
                readModel.type(),
                readModel.buildingId(),
                readModel.bjdongCd(),
                readModel.displayText(),
                readModel.lat(),
                readModel.lng()
        );
    }
}
