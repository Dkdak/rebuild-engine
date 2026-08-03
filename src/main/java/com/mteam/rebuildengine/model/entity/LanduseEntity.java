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

// F-06이 적재하는 토지이용계획정보(postgres/sql/load_landuse_csv.sql) — F-06 §3.2-2 용도지역 조회용
// 필드만 매핑. Java에서 save()하지 않는 테이블(trade/apartment_price와 동일 성격)이라 생성자 없음.
@Entity
@Table(name = "landuse")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LanduseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "building_id", length = 50)
    private String buildingId;

    @Column(name = "zone_name", length = 50)
    private String zoneName;
}
