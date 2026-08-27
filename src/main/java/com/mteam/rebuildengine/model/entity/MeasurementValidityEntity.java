package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// F-19 항목별 유효기간 참조 테이블(FEATURE_19_PERSONALIZED_ANALYSIS.md §2.2-b) — LAW-003 요율
// 테이블과 같은 방식으로 코드에 하드코딩하지 않는다. item_key는 measurement의 14개 항목 코드,
// validDays는 그 항목 입력 후 며칠 지나면 "재확인" 배지가 붙는지(30/90/180/null=안 낡음).
// SQL 시드 스크립트로만 채우는 테이블이라(cost_base_price 등과 동일 성격) 생성자를 따로 두지 않는다.
// 안전진단(item_key='SAFETY')만 예외로, 이 valid_days(180)의 기준일이 입력 시각이 아니라
// measurement.safety_inspection 안 진단일이다 — 서비스 레이어에서 처리(테이블 구조엔 영향 없음).
@Entity
@Table(name = "measurement_validity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeasurementValidityEntity {

    @Id
    @Column(name = "item_key", length = 40)
    private String itemKey;

    @Column(name = "valid_days")
    private Integer validDays;

    // §3.1-a(2026-08-24) — 기본값 INPUT_AT. 서류 있는 3개 항목(안전진단·실제 견적·증축 가능 연면적)만
    // DOCUMENT_DATE.
    @Enumerated(EnumType.STRING)
    @Column(name = "anchor", length = 20)
    private ValidityAnchor anchor;
}
