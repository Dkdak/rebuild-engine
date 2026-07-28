package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// GIS건물통합정보 Shapefile(F-14 데이터 이관) 적재 테이블. building(건축물대장 원본)과는
// building_gis_mapping을 통해서만 연결되며, 이 테이블 자체는 GIS 원본(A0~A28 29개 필드 전부) 그대로 유지한다.
// 필드 의미가 불확실한 것(rawXxx로 명명)은 FEATURE_14_GIS_DATA_MIGRATION.md §3.2 "미확정" 표기를 그대로 따른다.
@Entity
@Table(name = "gis_building",
        uniqueConstraints = @UniqueConstraint(columnNames = {"pnu", "building_ufid", "part_no"}),
        indexes = @Index(name = "idx_gis_building_address", columnList = "bjdongCd,mnLotno,subLotno"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GisBuildingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 19)
    private String pnu;

    @Column(name = "building_ufid", length = 20)
    private String buildingUfid;

    @Column(name = "part_no", nullable = false)
    private int partNo;

    // A0: 용도 불명(레코드 유일키 아님, 중복多 확인됨)
    private Integer rawA0;
    // A1: 용도 불명(28자리 숫자열, bdrg_sn과 무관 확인됨)
    @Column(length = 28)
    private String rawA1;

    @Column(length = 5)
    private String sigunguCd;
    @Column(length = 10)
    private String bjdongCd;
    @Column(length = 100)
    private String sggNm;
    @Column(length = 100)
    private String bjdongNm;
    @Column(length = 1)
    private String plotGbCd;
    @Column(length = 4)
    private String mnLotno;
    @Column(length = 4)
    private String subLotno;

    // A7: 대장구분(추정, 예: "일반")
    @Column(length = 254)
    private String ledgerGbNm;
    // A8: 의미 불명 코드(예: "01000")
    @Column(length = 5)
    private String rawA8;

    @Column(length = 254)
    private String mainPurposeNm;

    // A10: 의미 불명 코드(예: "39","11")
    @Column(length = 2)
    private String rawA10;

    @Column(length = 254)
    private String structureNm;

    private BigDecimal archArea;
    private LocalDate useApprovalDate;
    private BigDecimal totalFloorArea;
    private BigDecimal siteArea;
    private BigDecimal height;
    private BigDecimal buildingCoverageRatio;
    private BigDecimal floorAreaRatio;

    // A19: 일련번호(bdrg_sn 끝자리와 부분 일치하나 완전한 키는 아님, FEATURE_14 §3.2 참고)
    @Column(length = 28)
    private String rawA19;
    // A20: Y/N 플래그, 의미 불명
    @Column(length = 2)
    private String rawA20;

    // precision/scale을 안 정하면 Hibernate 기본값 numeric(19,2)로 생성돼 위경도가 소수점 2자리로
    // 반올림된다(0.01도 ≈ 약 1.1km) — 근처 건물들이 전부 같은 좌표로 뭉쳐 지도에서 겹치는 문제가
    // 실제로 발생(2026-07-27). 7자리면 센티미터급 정밀도.
    @Column(precision = 10, scale = 7)
    private BigDecimal centroidLat;
    @Column(precision = 10, scale = 7)
    private BigDecimal centroidLng;

    @Column(columnDefinition = "text")
    private String polygonGeojson;

    private LocalDate dataBaseDate;

    // A24, A25: 관측 샘플에서 전부 공백, 의미 불명
    @Column(length = 254)
    private String rawA24;
    @Column(length = 254)
    private String rawA25;
    // A26: 소분류 코드(추정)
    private Integer subCategoryCode;
    // A27: 플래그(추정)
    private Integer rawA27;
    // A28: 등록/수정일(추정)
    private LocalDate registeredDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @Column(nullable = false)
    private boolean isDeleted;

    @Builder
    public GisBuildingEntity(String pnu, String buildingUfid, int partNo, Integer rawA0, String rawA1,
                              String sigunguCd, String bjdongCd, String sggNm, String bjdongNm, String plotGbCd,
                              String mnLotno, String subLotno, String ledgerGbNm, String rawA8, String mainPurposeNm,
                              String rawA10, String structureNm, BigDecimal archArea, LocalDate useApprovalDate,
                              BigDecimal totalFloorArea, BigDecimal siteArea, BigDecimal height,
                              BigDecimal buildingCoverageRatio, BigDecimal floorAreaRatio, String rawA19,
                              String rawA20, BigDecimal centroidLat, BigDecimal centroidLng, String polygonGeojson,
                              LocalDate dataBaseDate, String rawA24, String rawA25, Integer subCategoryCode,
                              Integer rawA27, LocalDate registeredDate) {
        this.pnu = pnu;
        this.buildingUfid = buildingUfid;
        this.partNo = partNo;
        this.rawA0 = rawA0;
        this.rawA1 = rawA1;
        this.sigunguCd = sigunguCd;
        this.bjdongCd = bjdongCd;
        this.sggNm = sggNm;
        this.bjdongNm = bjdongNm;
        this.plotGbCd = plotGbCd;
        this.mnLotno = mnLotno;
        this.subLotno = subLotno;
        this.ledgerGbNm = ledgerGbNm;
        this.rawA8 = rawA8;
        this.mainPurposeNm = mainPurposeNm;
        this.rawA10 = rawA10;
        this.structureNm = structureNm;
        this.archArea = archArea;
        this.useApprovalDate = useApprovalDate;
        this.totalFloorArea = totalFloorArea;
        this.siteArea = siteArea;
        this.height = height;
        this.buildingCoverageRatio = buildingCoverageRatio;
        this.floorAreaRatio = floorAreaRatio;
        this.rawA19 = rawA19;
        this.rawA20 = rawA20;
        this.centroidLat = centroidLat;
        this.centroidLng = centroidLng;
        this.polygonGeojson = polygonGeojson;
        this.dataBaseDate = dataBaseDate;
        this.rawA24 = rawA24;
        this.rawA25 = rawA25;
        this.subCategoryCode = subCategoryCode;
        this.rawA27 = rawA27;
        this.registeredDate = registeredDate;
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

    // 일별 재수집 시 최신 값으로 갱신 (FEATURE_14 §3.1 "일별 제공" 반영)
    public void updateFrom(GisBuildingEntity fresh) {
        this.rawA0 = fresh.rawA0;
        this.rawA1 = fresh.rawA1;
        this.sigunguCd = fresh.sigunguCd;
        this.bjdongCd = fresh.bjdongCd;
        this.sggNm = fresh.sggNm;
        this.bjdongNm = fresh.bjdongNm;
        this.plotGbCd = fresh.plotGbCd;
        this.mnLotno = fresh.mnLotno;
        this.subLotno = fresh.subLotno;
        this.ledgerGbNm = fresh.ledgerGbNm;
        this.rawA8 = fresh.rawA8;
        this.mainPurposeNm = fresh.mainPurposeNm;
        this.rawA10 = fresh.rawA10;
        this.structureNm = fresh.structureNm;
        this.archArea = fresh.archArea;
        this.useApprovalDate = fresh.useApprovalDate;
        this.totalFloorArea = fresh.totalFloorArea;
        this.siteArea = fresh.siteArea;
        this.height = fresh.height;
        this.buildingCoverageRatio = fresh.buildingCoverageRatio;
        this.floorAreaRatio = fresh.floorAreaRatio;
        this.rawA19 = fresh.rawA19;
        this.rawA20 = fresh.rawA20;
        this.centroidLat = fresh.centroidLat;
        this.centroidLng = fresh.centroidLng;
        this.polygonGeojson = fresh.polygonGeojson;
        this.dataBaseDate = fresh.dataBaseDate;
        this.rawA24 = fresh.rawA24;
        this.rawA25 = fresh.rawA25;
        this.subCategoryCode = fresh.subCategoryCode;
        this.rawA27 = fresh.rawA27;
        this.registeredDate = fresh.registeredDate;
    }
}
