package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// F-16이 적재하는 개별공시지가정보(postgres/sql/load_land_price_csv.sql) — F-08 §3.6
// "토지당 가격" 조회용으로 필요한 필드만 매핑한다. ApartmentPriceEntity와 동일한 이유로 생성자 없음.
@Entity
@Table(name = "land_price")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LandPriceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "building_id", length = 50)
    private String buildingId;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "base_year")
    private Integer baseYear;

    @Column(name = "base_month")
    private Integer baseMonth;
}
