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
import java.time.LocalDate;

// F-15가 적재하는 실거래가 매매 6종(postgres/sql/load_trade_csv.sql) — F-04 §2.1-h "최근 실거래가"
// 조회용으로 필요한 필드만 매핑한다(HELP6 §5, buildingId+cancelDate 2조건 단순 조회). Java에서 save()하지
// 않고 SQL로만 채우는 테이블이라(search_index/investment_result와 동일 성격) 생성자를 따로 두지 않는다.
@Entity
@Table(name = "trade")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "building_id", length = 50)
    private String buildingId;

    @Column(name = "area_sqm")
    private BigDecimal areaSqm;

    @Column(name = "price_10k_won")
    private BigDecimal price10kWon;

    @Column(name = "contract_date")
    private LocalDate contractDate;

    @Column(name = "cancel_date")
    private LocalDate cancelDate;
}
