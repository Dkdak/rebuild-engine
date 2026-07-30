package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.service.InvestmentResultDummyDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// F-09 스파이크 테스트용 더미 grade/roi CSV 출력 수동 트리거 — DB에는 직접 안 쓰고 CSV만 만든다.
// 실제 investment_result 반영은 postgres/sql/load_investment_result.sql.
@RestController
@RequestMapping("/api/v1/admin/investment-result")
@RequiredArgsConstructor
public class AdminInvestmentResultController {

    private final InvestmentResultDummyDataService investmentResultDummyDataService;

    @PostMapping("/export")
    public ResponseEntity<InvestmentResultDummyDataService.ExportResult> export() {
        return ResponseEntity.ok(investmentResultDummyDataService.exportDummyDataCsv());
    }
}
