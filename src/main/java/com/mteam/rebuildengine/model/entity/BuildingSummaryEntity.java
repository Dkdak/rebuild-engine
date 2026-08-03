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

// F-17이 적재하는 건축물대장 총괄표제부(postgres/sql/load_building_summary_csv.sql) — F-05 §2.1
// "단지 정보" 카드(전체 세대수·동수·승강기수) 조회용 필드만 매핑. Java에서 save()하지 않는 테이블이라
// 생성자 없음(다른 이관 테이블과 동일 성격).
@Entity
@Table(name = "building_summary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuildingSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "building_id", length = 50)
    private String buildingId;

    @Column(name = "household_count")
    private Integer householdCount;

    @Column(name = "main_building_count")
    private Integer mainBuildingCount;

    @Column(name = "elevator_passenger_count")
    private Integer elevatorPassengerCount;

    @Column(name = "elevator_emergency_count")
    private Integer elevatorEmergencyCount;
}
