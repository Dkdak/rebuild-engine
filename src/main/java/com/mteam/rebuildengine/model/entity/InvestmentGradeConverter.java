package com.mteam.rebuildengine.model.entity;

import com.mteam.rebuildengine.utils.InvestmentGrade;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// investment_result.grade는 VARCHAR(2)("A+"/"A"/"B+"/"B"/"C"/"D")인데, InvestmentGrade enum 상수명은
// '+'를 못 써서 A_PLUS/B_PLUS다 — 기본 @Enumerated(EnumType.STRING)은 .name()("A_PLUS", 6자)을 그대로
// 저장해 컬럼 길이를 넘기므로 쓸 수 없다. displayName("A+")으로 직접 변환하는 컨버터를 대신 쓴다.
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
