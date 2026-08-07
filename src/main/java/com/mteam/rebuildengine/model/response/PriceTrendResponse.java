package com.mteam.rebuildengine.model.response;

import java.util.List;

// F-08 §3.8 "시세 추이" — 최근 36개월 월별 ㎡당 가격 중앙값(F-10 "시장 분석" 꺾은선 그래프용).
// matchStage: 0=법정동/1=구(§3.5와 같은 의미) — 범위 확대(2단계)는 쓰지 않는다, 그래프 전체가 한
// 완화 단계로 통일돼야 월별 비교가 왜곡되지 않기 때문(§3.8 설계). points는 결측월 생략.
public record PriceTrendResponse(int matchStage, List<PriceTrendPointResponse> points) {
}
