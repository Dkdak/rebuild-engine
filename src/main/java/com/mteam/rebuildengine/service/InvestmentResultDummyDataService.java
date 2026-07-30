package com.mteam.rebuildengine.service;

public interface InvestmentResultDummyDataService {
    // F-09(투자 분석) 정식 기획 전 스파이크 테스트용 — building 전체를 순회하며 결정론적 더미
    // grade/roi를 계산해 CSV로 내보낸다(DB에 직접 쓰지 않음). 실제 investment_result 반영은
    // postgres/sql/load_investment_result.sql이 담당(F-12/F-13/F-14와 동일한 CSV 경유 패턴).
    ExportResult exportDummyDataCsv();

    record ExportResult(int total) {
    }
}
