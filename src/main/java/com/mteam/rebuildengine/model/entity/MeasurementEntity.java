package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// F-19 실측 입력값 — 매물(user_id+building_id) 1건당 1행(FEATURE_19_PERSONALIZED_ANALYSIS.md §3.1
// "저장 단위는 매물 1건당 실측 1벌"). F-11 favorite와 별개 테이블 — 관심목록은 참조만, 이쪽은 14개
// 항목의 실제 입력값을 담는다(계산 반영 9 + 기록 5, product §2.3-b 표가 단일 출처).
//
// wide 테이블(항목당 explicit 컬럼) + 안전진단만 jsonb 하이브리드(2026-08-24 확정) — 나머지 13개
// 항목은 타입 안전성·쿼리 편의성 때문에 explicit 컬럼으로 두고, 안전진단만 LAW-004(조사 예정) 결과에
// 따라 서브필드(등급 체계·허용 증축 방식 구분)가 바뀔 수 있어 jsonb로 유연하게 열어둔다.
//
// 입력 시각은 "항목 단위"다(서브필드 단위 아님) — 안전진단은 등급/진단일/허용방식 3개 서브필드가 있어도
// safetyInputAt 하나, 설계비·인허가비도 2개 값이지만 designPermitFeeInputAt 하나. 이게 §재확인 판정
// (앞 단계 값 변경 시각 vs 뒤 단계 입력 시각)과 §시간 경과 판정(입력 시각 + measurement_validity.
// valid_days)의 기준이 된다 — 별도 상태 플래그 컬럼을 두지 않고 이 시각들만으로 서비스 레이어가 판정한다.
// 안전진단만 예외로, 시간 경과 판정 기준일이 safetyInputAt이 아니라 safetyInspection의 진단일이다
// (product 확정, 2026-08-24 §2.2-d 정정 — 오래된 진단서를 오늘 입력해도 오늘부터 180일이 새로 시작되면
// 안 되기 때문).
@Entity
@Table(name = "measurement",
        uniqueConstraints = @UniqueConstraint(name = "uk_measurement_user_building", columnNames = {"user_id", "building_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeasurementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "building_id", nullable = false, length = 50)
    private String buildingId;

    // ── STEP 1 규제 확인 ──────────────────────────────────────────────
    @Column(name = "zone_name", length = 100)
    private String zoneName;
    @Column(name = "far_limit_pct")
    private BigDecimal farLimitPct;
    @Column(name = "zoning_input_at")
    private LocalDateTime zoningInputAt;

    @Column(name = "height_limit", length = 500)
    private String heightLimit;
    @Column(name = "district_plan", length = 500)
    private String districtPlan;
    @Column(name = "height_limit_input_at")
    private LocalDateTime heightLimitInputAt;

    // {grade, inspectionDate, allowedExpansionType} — LAW-004 결과에 따라 서브필드가 바뀔 수 있어 jsonb.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "safety_inspection", columnDefinition = "jsonb")
    private String safetyInspection;
    @Column(name = "safety_input_at")
    private LocalDateTime safetyInputAt;

    // ── STEP 2 건축사 사전검토 ─────────────────────────────────────────
    @Column(name = "actual_expandable_area_sqm")
    private BigDecimal actualExpandableAreaSqm;
    // §3.1-a(2026-08-24) — 검토서 작성일, 선택 입력. 있으면 유효기간 기준일로 이걸 쓴다(없으면 입력 시각).
    @Column(name = "expandable_area_document_date")
    private LocalDate expandableAreaDocumentDate;
    @Column(name = "expandable_area_input_at")
    private LocalDateTime expandableAreaInputAt;

    @Column(name = "reduction_reason", length = 1000)
    private String reductionReason;
    @Column(name = "reduction_reason_input_at")
    private LocalDateTime reductionReasonInputAt;

    // ── STEP 3 공사비 견적 ────────────────────────────────────────────
    @Column(name = "actual_construction_estimate")
    private BigDecimal actualConstructionEstimate;
    // §3.1-a(2026-08-24) — 견적서 발행일, 선택 입력. 있으면 유효기간 기준일로 이걸 쓴다(없으면 입력 시각).
    @Column(name = "estimate_document_date")
    private LocalDate estimateDocumentDate;
    @Column(name = "estimate_input_at")
    private LocalDateTime estimateInputAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "estimate_site_condition", length = 20)
    private SiteCondition estimateSiteCondition;
    @Column(name = "estimate_site_note", length = 1000)
    private String estimateSiteNote;
    @Column(name = "estimate_source", length = 500)
    private String estimateSource;
    @Column(name = "estimate_basis_input_at")
    private LocalDateTime estimateBasisInputAt;

    @Column(name = "design_fee")
    private BigDecimal designFee;
    @Column(name = "permit_fee")
    private BigDecimal permitFee;
    @Column(name = "design_permit_fee_input_at")
    private LocalDateTime designPermitFeeInputAt;

    // ── STEP 4 매입 조건 ──────────────────────────────────────────────
    @Column(name = "actual_purchase_price")
    private BigDecimal actualPurchasePrice;
    @Column(name = "purchase_price_input_at")
    private LocalDateTime purchasePriceInputAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "acquisition_entity_type", length = 30)
    private AcquisitionEntityType acquisitionEntityType;
    @Column(name = "acquisition_entity_input_at")
    private LocalDateTime acquisitionEntityInputAt;

    @Column(name = "registry_rights_status", length = 1000)
    private String registryRightsStatus;
    @Column(name = "registry_rights_input_at")
    private LocalDateTime registryRightsInputAt;

    @Column(name = "lease_vacancy_condition", length = 1000)
    private String leaseVacancyCondition;
    @Column(name = "lease_vacancy_input_at")
    private LocalDateTime leaseVacancyInputAt;

    // ── STEP 5 미래가치 ───────────────────────────────────────────────
    @Column(name = "post_remodel_estimated_price")
    private BigDecimal postRemodelEstimatedPrice;
    @Column(name = "future_value_input_at")
    private LocalDateTime futureValueInputAt;

    @Column(name = "valuation_basis_memo", length = 1000)
    private String valuationBasisMemo;
    @Column(name = "future_value_basis_input_at")
    private LocalDateTime futureValueBasisInputAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @Column(nullable = false)
    private boolean isDeleted;

    // 최초 생성 시점엔 user_id+building_id만 있으면 된다 — 14개 항목 값은 STEP 단위 저장 API(F-19,
    // 다음 단계 구현 예정)가 채운다. 단계별 update 메서드는 그 API를 구현할 때 추가한다.
    @Builder
    public MeasurementEntity(Long userId, String buildingId) {
        this.userId = userId;
        this.buildingId = buildingId;
        this.isDeleted = false;
    }

    // 소프트 삭제 후("실측 전체 삭제 = 공공데이터 기준으로 되돌리기") 재측정 시 이 행을 재사용한다 —
    // FavoriteEntity.reactivate()와 같은 이유(UNIQUE(user_id, building_id) 제약 회피). 값은 전부
    // 새로 입력받으므로 여기서 초기화하지 않는다 — 각 updateXxx()가 개별 항목을 채운다.
    public void reactivate() {
        this.isDeleted = false;
    }

    public void markDeleted() {
        this.isDeleted = true;
    }

    // ── STEP별 항목 갱신 — 값이 바뀌었는지는 서비스 레이어(MeasurementServiceImpl)가 저장 전에
    // 이전 값과 비교해 이력을 남긴다. 여기는 단순히 새 값 + 입력 시각을 반영만 한다.
    public void updateZoning(String zoneName, BigDecimal farLimitPct, LocalDateTime inputAt) {
        this.zoneName = zoneName;
        this.farLimitPct = farLimitPct;
        this.zoningInputAt = inputAt;
    }

    public void updateHeightLimit(String heightLimit, String districtPlan, LocalDateTime inputAt) {
        this.heightLimit = heightLimit;
        this.districtPlan = districtPlan;
        this.heightLimitInputAt = inputAt;
    }

    public void updateSafety(String safetyInspectionJson, LocalDateTime inputAt) {
        this.safetyInspection = safetyInspectionJson;
        this.safetyInputAt = inputAt;
    }

    public void updateExpandableArea(BigDecimal areaSqm, LocalDate documentDate, LocalDateTime inputAt) {
        this.actualExpandableAreaSqm = areaSqm;
        this.expandableAreaDocumentDate = documentDate;
        this.expandableAreaInputAt = inputAt;
    }

    public void updateReductionReason(String reason, LocalDateTime inputAt) {
        this.reductionReason = reason;
        this.reductionReasonInputAt = inputAt;
    }

    public void updateEstimate(BigDecimal amount, LocalDate documentDate, LocalDateTime inputAt) {
        this.actualConstructionEstimate = amount;
        this.estimateDocumentDate = documentDate;
        this.estimateInputAt = inputAt;
    }

    public void updateEstimateBasis(SiteCondition siteCondition, String siteNote, String source, LocalDateTime inputAt) {
        this.estimateSiteCondition = siteCondition;
        this.estimateSiteNote = siteNote;
        this.estimateSource = source;
        this.estimateBasisInputAt = inputAt;
    }

    public void updateDesignPermitFee(BigDecimal designFee, BigDecimal permitFee, LocalDateTime inputAt) {
        this.designFee = designFee;
        this.permitFee = permitFee;
        this.designPermitFeeInputAt = inputAt;
    }

    public void updatePurchasePrice(BigDecimal price, LocalDateTime inputAt) {
        this.actualPurchasePrice = price;
        this.purchasePriceInputAt = inputAt;
    }

    public void updateAcquisitionEntity(AcquisitionEntityType type, LocalDateTime inputAt) {
        this.acquisitionEntityType = type;
        this.acquisitionEntityInputAt = inputAt;
    }

    public void updateRegistryRights(String status, LocalDateTime inputAt) {
        this.registryRightsStatus = status;
        this.registryRightsInputAt = inputAt;
    }

    public void updateLeaseVacancy(String condition, LocalDateTime inputAt) {
        this.leaseVacancyCondition = condition;
        this.leaseVacancyInputAt = inputAt;
    }

    public void updateFutureValue(BigDecimal price, LocalDateTime inputAt) {
        this.postRemodelEstimatedPrice = price;
        this.futureValueInputAt = inputAt;
    }

    public void updateFutureValueBasis(String memo, LocalDateTime inputAt) {
        this.valuationBasisMemo = memo;
        this.futureValueBasisInputAt = inputAt;
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
