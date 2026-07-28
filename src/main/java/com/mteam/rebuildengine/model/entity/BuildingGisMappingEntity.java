package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// building(원본) <-> gis_building(원본) 매칭 배치의 가공 결과. PNU는 원본 데이터가 아니라
// 매칭을 위해 계산한 값이라 building에는 저장하지 않고 여기에만 둔다.
@Entity
@Table(name = "building_gis_mapping")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuildingGisMappingEntity {

    // 다른 엔티티는 전부 IDENTITY(BIGSERIAL)를 쓰지만, 이 테이블은 585K건 규모로 매 배치마다 대량 INSERT가
    // 발생해서 예외적으로 SEQUENCE를 쓴다 — IDENTITY는 Hibernate가 JDBC 배치 INSERT를 못 묶는 제약이 있다.
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "building_gis_mapping_seq")
    @SequenceGenerator(name = "building_gis_mapping_seq", sequenceName = "building_gis_mapping_seq", allocationSize = 100)
    private Long id;

    @Column(name = "building_id", nullable = false, unique = true, length = 50)
    private String buildingId;

    @Column(name = "gis_building_id")
    private Long gisBuildingId;

    @Column(length = 19)
    private String pnu;

    @Column(nullable = false)
    private int candidateCount;

    @Column(nullable = false, length = 30)
    private String matchType;

    private BigDecimal score;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @Column(nullable = false)
    private boolean isDeleted;

    @Builder
    public BuildingGisMappingEntity(String buildingId, Long gisBuildingId, String pnu, int candidateCount,
                                     String matchType, BigDecimal score) {
        this.buildingId = buildingId;
        this.gisBuildingId = gisBuildingId;
        this.pnu = pnu;
        this.candidateCount = candidateCount;
        this.matchType = matchType;
        this.score = score;
        this.isDeleted = false;
    }

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
