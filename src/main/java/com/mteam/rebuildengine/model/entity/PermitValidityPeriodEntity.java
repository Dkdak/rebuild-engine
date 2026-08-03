package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// docs/law/LAW-001 §4 — 인허가 유효기간(postgres/sql/seed_permit_validity_period.sql), 단일 행.
@Entity
@Table(name = "permit_validity_period")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PermitValidityPeriodEntity {

    @Id
    private Long id;

    @Column(name = "validity_years")
    private int validityYears;
}
