package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// F-07이 시드하는 용도지수 참조표(postgres/sql/seed_usage_index.sql) — code가 PK(APT/MULTI/OFFICETEL/
// COMMERCIAL/FACTORY). PropertyType과 1:1이 아니라 ROW_HOUSE·SINGLE_FAMILY가 MULTI로 합쳐지므로
// PropertyTypeClassifier 결과를 이 code로 변환하는 매핑은 CostServiceImpl이 담당한다.
@Entity
@Table(name = "usage_index")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UsageIndexEntity {

    @Id
    @Column(name = "code", length = 20)
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "index")
    private int index;
}
