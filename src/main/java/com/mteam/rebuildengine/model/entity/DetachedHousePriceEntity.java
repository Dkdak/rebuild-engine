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

// F-16이 적재하는 개별주택가격정보(postgres/sql/load_detached_house_price_csv.sql) — 단독주택·다가구주택
// 공시가격, apartment_price(공동주택가격)가 커버하지 못하는 유형을 채운다. F-08 §3.6 "공시가격" 조회 시
// apartment_price에 없으면 이쪽을 본다. LandPriceEntity와 동일한 이유로 생성자 없음.
@Entity
@Table(name = "detached_house_price")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DetachedHousePriceEntity {

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
