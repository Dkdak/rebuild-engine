package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// F-06이 시드하는 용도지역 상한표(postgres/sql/seed_zoning_limit.sql) — zone_name이 PK.
// SQL로만 채우는 테이블이라 생성자 없음(다른 이관 테이블과 동일 성격).
@Entity
@Table(name = "zoning_limit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoningLimitEntity {

    @Id
    @Column(name = "zone_name", length = 50)
    private String zoneName;

    @Column(name = "coverage_ratio_limit")
    private BigDecimal coverageRatioLimit;

    @Column(name = "floor_area_ratio_limit")
    private BigDecimal floorAreaRatioLimit;
}
