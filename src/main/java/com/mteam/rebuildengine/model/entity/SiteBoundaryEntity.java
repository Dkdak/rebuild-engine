package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 연속지적도(필지 경계) Shapefile 적재 테이블 — FEATURE_05_PROPERTY_INFO.md §5.1 Open Item ③
// "siteBoundaryPolygon"(대지 경계, sitePolygon=건물 외곽선과는 다른 폴리곤). gis_building(F-14)과
// 같은 파이프라인(ShpReader/DbfReader/CoordinateReprojector/GeoUtils 재사용)으로 적재하되, 소스
// 데이터셋 자체가 다르다(V-World dsId=30563≒23, "AL_D002" 계열, 필지 단위라 pnu가 유일키 — 건물처럼
// 한 필지에 여러 파트가 있는 gis_building과 다름). pnu로 gis_building과 조인해 building까지 연결한다
// (2026-08-09, 실측 매칭률 99.87% 확인 — FEATURE_05_PROPERTY_INFO.md §5.1).
@Entity
@Table(name = "site_boundary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteBoundaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 19)
    private String pnu;

    @Column(length = 10)
    private String bjdongCd;
    @Column(length = 5)
    private String sigunguCd;

    // 지번(본번-부번, 예: "52-28") — 원본 A4 그대로, building.mnLotno/subLotno처럼 분리하지 않는다
    // (site_boundary는 pnu 매칭이 주 목적이라 지번 분해가 당장 필요 없음, 필요해지면 pnu에서 파생 가능).
    @Column(length = 50)
    private String lotAddress;
    @Column(length = 300)
    private String fullAddress;

    @Column(columnDefinition = "text")
    private String polygonGeojson;

    private LocalDate dataBaseDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @Column(nullable = false)
    private boolean isDeleted;

    @Builder
    public SiteBoundaryEntity(String pnu, String bjdongCd, String sigunguCd, String lotAddress, String fullAddress,
                               String polygonGeojson, LocalDate dataBaseDate) {
        this.pnu = pnu;
        this.bjdongCd = bjdongCd;
        this.sigunguCd = sigunguCd;
        this.lotAddress = lotAddress;
        this.fullAddress = fullAddress;
        this.polygonGeojson = polygonGeojson;
        this.dataBaseDate = dataBaseDate;
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
