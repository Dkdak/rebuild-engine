package com.mteam.rebuildengine.service.analysis;

public interface InvestmentAnalysisBatchService {
    // FEATURE_09_INVESTMENT.md §3.4 — building 전체를 순회하며 F-09 V1 공식(F-06+F-07+F-08 결합)으로
    // 실제 grade/roi/basis 스냅샷을 계산해 CSV로 내보낸다(DB에 직접 쓰지 않음). 실제 investment_result
    // 반영은 postgres/sql/load_investment_result.sql이 담당(F-12/F-13/F-14와 동일한 CSV 경유 패턴).
    ExportResult exportAnalysisCsv();

    record ExportResult(int total, int skipped) {
    }
}
