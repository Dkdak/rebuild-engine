package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// F-19 실측 입력 이력(FEATURE_19_PERSONALIZED_ANALYSIS.md §3.1) — 값이 실제로 바뀐 저장만 남긴다
// (열어서 확인만 하고 같은 값으로 저장하면 measurement.xxxInputAt만 갱신하고 이력에 안 넣는다).
// append-only 로그라 is_deleted/updated_at 없음(dashboard_stats_snapshot과 같은 성격) — 절대 UPDATE 안 함.
// previousValue/newValue는 항목마다 값의 모양이 달라(단일 숫자부터 안전진단 3서브필드까지) jsonb로
// 유연하게 담는다 — measurement 본문(explicit 컬럼)과 달리 이력은 항목별로 이미 이질적인 데이터라
// 정형 컬럼으로 나눌 이득이 없다.
@Entity
@Table(name = "measurement_history",
        indexes = @Index(name = "idx_measurement_history_user_building", columnList = "user_id, building_id, changed_at DESC"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeasurementHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "building_id", nullable = false, length = 50)
    private String buildingId;

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    // measurement의 14개 항목 중 하나를 가리키는 코드 — MeasurementItem enum(다음 단계 구현 예정) 값.
    @Column(name = "item_key", nullable = false, length = 40)
    private String itemKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_value", columnDefinition = "jsonb")
    private String previousValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private String newValue;

    // 그 시점 재계산 ROI — 과거 시점을 재계산하려면 당시 공공데이터 스냅샷이 필요해지므로 변경 시점에
    // 값을 그대로 저장해둔다(§3.1).
    @Column(name = "measured_roi_at_change")
    private BigDecimal measuredRoiAtChange;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Builder
    public MeasurementHistoryEntity(Long userId, String buildingId, int stepNo, String itemKey,
                                     String previousValue, String newValue, BigDecimal measuredRoiAtChange) {
        this.userId = userId;
        this.buildingId = buildingId;
        this.stepNo = stepNo;
        this.itemKey = itemKey;
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.measuredRoiAtChange = measuredRoiAtChange;
        this.changedAt = LocalDateTime.now();
    }
}
