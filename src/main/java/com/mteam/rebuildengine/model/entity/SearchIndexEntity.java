package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// building+building_gis_mapping+gis_building+legal_dong_code 조인 파생 테이블(F-13 §3.7).
// 매번 TRUNCATE 후 전체 재생성하므로 감사(audit) 컬럼이 없다. search_text의 GIN 트라이그램 인덱스는
// Hibernate가 만들 수 없어(연산자 클래스 지정 불가) postgres/sql/load_search_index.sql에서만 관리한다
// — 그래서 이 엔티티엔 @Index를 선언하지 않는다(선언하면 불필요한 일반 btree 인덱스가 추가로 생김).
@Entity
@Table(name = "search_index")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchIndexEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String type;

    @Column(name = "building_id", length = 50)
    private String buildingId;

    @Column(name = "bjdong_cd", length = 10)
    private String bjdongCd;

    @Column(name = "display_text", nullable = false, columnDefinition = "text")
    private String displayText;

    // precision/scale 미지정 시 Hibernate 기본값 numeric(19,2)로 생성돼 위경도가 소수점 2자리로
    // 반올림되는 문제가 있다(GisBuildingEntity와 동일 원인, 2026-07-27) — 7자리로 명시.
    @Column(precision = 10, scale = 7)
    private BigDecimal lat;
    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(name = "search_text", nullable = false, columnDefinition = "text")
    private String searchText;

    @Builder
    public SearchIndexEntity(String type, String buildingId, String bjdongCd, String displayText,
                              BigDecimal lat, BigDecimal lng, String searchText) {
        this.type = type;
        this.buildingId = buildingId;
        this.bjdongCd = bjdongCd;
        this.displayText = displayText;
        this.lat = lat;
        this.lng = lng;
        this.searchText = searchText;
    }
}
