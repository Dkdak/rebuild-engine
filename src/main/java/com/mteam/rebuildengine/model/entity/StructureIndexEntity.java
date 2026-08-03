package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// F-07이 시드하는 구조지수·내용연수 참조표(postgres/sql/seed_structure_index.sql) — code가 PK.
@Entity
@Table(name = "structure_index")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StructureIndexEntity {

    @Id
    @Column(name = "code", length = 20)
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "index")
    private int index;

    @Column(name = "life_year")
    private int lifeYear;
}
