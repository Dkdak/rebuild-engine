package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 법정동코드 <-> 법정동명 참조 테이블. GIS 데이터(F-14) 파싱 과정에서 부산물로 확보하며,
// building(F-13, 이름만 있음)의 이름을 코드로 변환할 때 쓴다 (building_gis_mapping 매칭 배치 전용).
@Entity
@Table(name = "legal_dong_code", indexes = @Index(name = "idx_legal_dong_code_name", columnList = "sggNm,bjdongNm"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalDongCodeEntity {

    @Id
    @Column(length = 10)
    private String bjdongCd;

    @Column(nullable = false, length = 5)
    private String sigunguCd;

    @Column(nullable = false, length = 100)
    private String sggNm;

    @Column(nullable = false, length = 100)
    private String bjdongNm;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @Column(nullable = false)
    private boolean isDeleted;

    @Builder
    public LegalDongCodeEntity(String bjdongCd, String sigunguCd, String sggNm, String bjdongNm) {
        this.bjdongCd = bjdongCd;
        this.sigunguCd = sigunguCd;
        this.sggNm = sggNm;
        this.bjdongNm = bjdongNm;
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
