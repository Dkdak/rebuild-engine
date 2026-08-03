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

import java.time.LocalDate;

// F-06이 적재하는 건축인허가정보(postgres/sql/load_permit_csv.sql) — F-06 §3.2-3 진행중 개발행위
// 판정용 필드만 매핑. LanduseEntity와 동일한 이유로 생성자 없음.
@Entity
@Table(name = "permit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PermitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "building_id", length = 50)
    private String buildingId;

    @Column(name = "build_type", length = 20)
    private String buildType;

    @Column(name = "permit_date")
    private LocalDate permitDate;

    @Column(name = "use_approval_date")
    private LocalDate useApprovalDate;
}
