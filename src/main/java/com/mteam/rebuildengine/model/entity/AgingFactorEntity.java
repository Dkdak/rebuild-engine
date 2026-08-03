package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// F-07이 시드하는 구조별 노후도 보정계수 배율(k) 참조표(postgres/sql/seed_aging_factor.sql) — structure가
// structure_index.code를 참조하는 PK. min/max로 공사비 최소~최대 범위를 산정한다(FEATURE_07_COST.md §3.2 item 4~5).
@Entity
@Table(name = "aging_factor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgingFactorEntity {

    @Id
    @Column(name = "structure", length = 20)
    private String structure;

    @Column(name = "min_factor")
    private BigDecimal minFactor;

    @Column(name = "default_factor")
    private BigDecimal defaultFactor;

    @Column(name = "max_factor")
    private BigDecimal maxFactor;
}
