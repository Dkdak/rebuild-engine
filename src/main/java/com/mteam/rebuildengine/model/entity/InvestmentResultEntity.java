package com.mteam.rebuildengine.model.entity;

import com.mteam.rebuildengine.utils.InvestmentGrade;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// F-09(투자 분석) V1 결과 — building_id(=building.bdrg_sn) 기준 F-06+F-07+F-08을 결합한 실제 계산값
// (postgres/sql/load_investment_result.sql, InvestmentAnalysisBatchService). Java에서 save()하지
// 않고 SQL로만 채우는 테이블이라(search_index/building_gis_mapping과 동일 성격) 생성자를 따로 두지
// 않는다. remodeling_basis/cost_basis/market_basis는 F-06/F-07/F-08 응답을 그대로 JSON 직렬화한
// 것 — F-05/F-10이 실시간 API 3개 대신 이 스냅샷을 읽도록 전환(FEATURE_09_INVESTMENT.md §3.4).
@Entity
@Table(name = "investment_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvestmentResultEntity {

    @Id
    @Column(name = "building_id", length = 50)
    private String buildingId;

    // 2026-08-09 변경 — SCORE_FALLBACK이면 InvestmentGrade.NA("정보부족"), 값은 항상 존재(null 아님).
    // 예전엔 F-06 achievementRate로 A~D 등급을 매겼으나 grade 인플레이션 문제로 산출 자체를 포기하고
    // NA로 명확히 구분(§3.2 기획 결정, InvestmentServiceImpl 참고).
    @Convert(converter = InvestmentGradeConverter.class)
    @Column(nullable = false, length = 2)
    private InvestmentGrade grade;

    private BigDecimal roi;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "remodeling_basis", columnDefinition = "jsonb")
    private String remodelingBasis;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cost_basis", columnDefinition = "jsonb")
    private String costBasis;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "market_basis", columnDefinition = "jsonb")
    private String marketBasis;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @Column(nullable = false)
    private boolean isDeleted;

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
