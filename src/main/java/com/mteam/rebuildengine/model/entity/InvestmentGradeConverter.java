package com.mteam.rebuildengine.model.entity;

import com.mteam.rebuildengine.utils.InvestmentGrade;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// investment_result.grade는 VARCHAR(2)("A"/"B"/"C"/"D"/"NA", §3.3 2026-08-09 등급체계 축소). 지금은
// enum 상수명과 displayName이 같아 @Enumerated(EnumType.STRING)로도 충분하지만, 예전 6종(A+/B+ 포함)
// 체계에서 '+'를 상수명에 못 써 만든 변환 계층을 그대로 유지 — 등급 구간이 다시 세분화될 가능성에 대비.
@Converter(autoApply = false)
public class InvestmentGradeConverter implements AttributeConverter<InvestmentGrade, String> {

    @Override
    public String convertToDatabaseColumn(InvestmentGrade grade) {
        return grade != null ? grade.getDisplayName() : null;
    }

    @Override
    public InvestmentGrade convertToEntityAttribute(String dbValue) {
        return dbValue != null
                ? InvestmentGrade.fromDisplayName(dbValue).orElseThrow(() ->
                        new IllegalStateException("알 수 없는 investment_result.grade 값: " + dbValue))
                : null;
    }
}
