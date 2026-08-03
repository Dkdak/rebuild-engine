package com.mteam.rebuildengine.model.response;

// F-06 §3.6 GET /api/v1/properties/{buildingId}/remodeling 응답. score는 게이트 미달로 즉시
// NOT_POSSIBLE 처리된 경우 null("점수와 무관하게 불가", §3.2-5).
public record RemodelingResultResponse(Integer score, RemodelingVerdict verdict, RemodelingBasisResponse basis) {
}
