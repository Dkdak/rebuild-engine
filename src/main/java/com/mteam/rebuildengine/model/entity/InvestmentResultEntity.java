package com.mteam.rebuildengine.model.entity;

import com.mteam.rebuildengine.utils.InvestmentGrade;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// F-09(투자 분석) 정식 기획 전 스파이크 테스트용 더미 결과 — building_id(=building.bdrg_sn) 기준
// 결정론적 해시로 채워진다(postgres/sql/load_investment_result.sql). Java에서 save()하지 않고 SQL로만
// 채우는 테이블이라(search_index/building_gis_mapping과 동일 성격) 생성자를 따로 두지 않는다.
// F-06(리모델링점수)·F-08(시세) 완료 후 정식 F-09 기획에서 테이블 구조·계산 로직이 다시 바뀔 수 있다.
@Entity
@Table(name = "investment_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvestmentResultEntity {

    @Id
    @Column(name = "building_id", length = 50)
    private String buildingId;

    @Convert(converter = InvestmentGradeConverter.class)
    @Column(nullable = false, length = 2)
    private InvestmentGrade grade;

    private BigDecimal roi;

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
