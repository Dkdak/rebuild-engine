package com.mteam.rebuildengine.utils;

import java.util.Arrays;
import java.util.Optional;

// FEATURE_09_INVESTMENT.md §3.3(2026-08-1x) 등급 4종(A/B/C/D) + NA(정보 부족, SCORE_FALLBACK 전용).
// 원래 6종(A+~D)이었으나 ROI 수치가 등급 옆에 항상 같이 노출돼(예: "A · ROI 18.2%") 세밀한 구간
// 구분을 글자 등급까지 쪼갤 필요가 없다고 판단해 인접 등급을 통합. displayName은 enum 상수명과 동일해
// InvestmentGradeConverter는 이제 사실상 항등 변환이지만, DB 컬럼값이 이 enum의 표시값이라는 관계 자체는
// 유지(향후 다시 갈라질 수 있음을 대비, "A_PLUS" 같은 우회 표기가 다시 필요해질 가능성).
public enum InvestmentGrade {
    A("A"),
    B("B"),
    C("C"),
    D("D"),
    NA("NA");

    private final String displayName;

    InvestmentGrade(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Optional<InvestmentGrade> fromDisplayName(String displayName) {
        return Arrays.stream(values()).filter(grade -> grade.displayName.equals(displayName)).findFirst();
    }
}
