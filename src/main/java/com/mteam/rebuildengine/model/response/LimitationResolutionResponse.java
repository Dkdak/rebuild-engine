package com.mteam.rebuildengine.model.response;

// FEATURE_10_REFERENCE.md "08 근거 및 참고자료 — 분석의 한계" 5개 문장의 CASE2 해소 현황
// (FEATURE_19_PERSONALIZED_ANALYSIS.md §1.1: "분석의 한계 항목별로 해소 현황(해소/일부/기록됨/미해소)을
// 표시한다"). status 값:
// - RESOLVED(해소): 대응하는 계산 반영 항목이 전부 실측됨(ROI 등 실제 값이 바뀜)
// - PARTIAL(일부): 대응 항목 중 일부만 실측됨
// - RECORDED(기록됨): 대응 항목이 계산 미반영(기록 전용)이라 채워도 값은 안 바뀌지만 기록은 남음 —
//   이 항목의 최대치(더 이상 못 올라감)
// - UNRESOLVED(미해소): 대응 항목이 있는데 하나도 안 채워짐 — "입력하면 해소된다"는 뜻
// - OUT_OF_SCOPE(분석 범위 아님, §2.3-d 2026-08-24 확정): F-19가 애초에 안 다루기로 한 항목(세금·
//   금융비용·보유비용) — 사용자가 뭘 입력해도 안 풀린다는 점이 UNRESOLVED와 다르다("입력하면
//   풀린다"는 뉘앙스를 주면 안 됨)
public record LimitationResolutionResponse(int number, String statement, String status) {
}
