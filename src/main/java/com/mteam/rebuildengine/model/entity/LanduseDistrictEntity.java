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

// F-06이 적재하는 지구/구역 지정 여부(postgres/sql/load_landuse_district_csv.sql) — §2.1 판단 근거
// 표시 전용, 스코어링엔 안 들어감(LanduseEntity의 zone_name과 별개). 한 필지에 여러 지정이 있을 수
// 있어(1:N) building_id 기준 여러 행이 정상.
@Entity
@Table(name = "landuse_district")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LanduseDistrictEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "building_id", length = 50)
    private String buildingId;

    @Column(name = "district_name", length = 100)
    private String districtName;
}
