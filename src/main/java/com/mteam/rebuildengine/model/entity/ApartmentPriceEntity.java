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

// F-16이 적재하는 공동주택가격정보(postgres/sql/load_apartment_price_csv.sql) — F-08 §3.6
// "공시가격" 조회용으로 필요한 필드만 매핑한다. Java에서 save()하지 않고 SQL로만 채우는 테이블이라
// (trade/search_index와 동일 성격) 생성자를 따로 두지 않는다.
@Entity
@Table(name = "apartment_price")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApartmentPriceEntity {

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
