package com.mteam.rebuildengine.service.analysis;

import com.mteam.rebuildengine.model.request.MeasurementStepSaveRequest;
import com.mteam.rebuildengine.model.response.BuildingReportResponse;
import com.mteam.rebuildengine.model.response.MeasurementDetailResponse;
import com.mteam.rebuildengine.model.response.MeasurementHistoryEntryResponse;
import com.mteam.rebuildengine.model.response.MeasurementListItemResponse;
import com.mteam.rebuildengine.model.response.MeasurementStepSaveResponse;
import com.mteam.rebuildengine.model.response.ZoningLimitResponse;

import java.util.List;
import java.util.Optional;

// FEATURE_19_PERSONALIZED_ANALYSIS.md §3.2 — F-19 실측 입력.
public interface MeasurementService {
    List<MeasurementListItemResponse> list(String email);

    // §2.2-e — STEP1 용도지역 드롭다운용, zoning_limit 16종 전체(사용자 무관, 참조 테이블 그대로).
    List<ZoningLimitResponse> listZoningLimits();

    Optional<MeasurementDetailResponse> get(String email, String buildingId);

    // 저장 단위는 단계(§2.2-b) — stepNo(1~5)에 해당하는 필드만 request에서 읽는다.
    MeasurementStepSaveResponse saveStep(String email, String buildingId, int stepNo, MeasurementStepSaveRequest request);

    List<MeasurementHistoryEntryResponse> history(String email, String buildingId);

    // 실측 전체 삭제(공공데이터 기준으로 되돌리기) — 소프트 삭제, 이력은 유지.
    void delete(String email, String buildingId);

    // FEATURE_19 §1.1 — F-10 리포트 CASE1/CASE2 통합. 활성 실측이 있으면 CASE2(측정값 우선), 없으면
    // CASE1(전부 추정)과 같은 내용이 된다.
    BuildingReportResponse getReport(String email, String buildingId);
}
