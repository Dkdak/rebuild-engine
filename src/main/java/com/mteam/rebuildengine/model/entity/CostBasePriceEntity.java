package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

// F-07이 시드하는 공사비 기준단가 참조표(postgres/sql/seed_cost_base_price.sql) — effective_date가
// PK, 고시 개정마다 행이 누적된다(cost_law_constant 단일행 설계 폐기, FEATURE_07_COST.md §3.2).
@Entity
@Table(name = "cost_base_price")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CostBasePriceEntity {

    @Id
    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "base_price")
    private BigDecimal basePrice;

    @Column(name = "source")
    private String source;
}
