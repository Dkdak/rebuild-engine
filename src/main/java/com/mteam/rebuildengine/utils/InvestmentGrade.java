package com.mteam.rebuildengine.utils;

import java.util.Arrays;
import java.util.Optional;

// F-09(투자 분석) 정식 기획 전 스파이크 테스트용 등급 6종. Java enum 상수명에 '+'를 못 써서 A_PLUS/B_PLUS로
// 짓고, DB·API에는 displayName("A+"/"B+")으로 노출한다(InvestmentGradeConverter가 변환 담당).
public enum InvestmentGrade {
    A_PLUS("A+"),
    A("A"),
    B_PLUS("B+"),
    B("B"),
    C("C"),
    D("D");

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
