package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.service.InvestmentAnalysisBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// F-09 V1 grade/roi/basis CSV 출력 수동 트리거 — DB에는 직접 안 쓰고 CSV만 만든다.
// 실제 investment_result 반영은 postgres/sql/load_investment_result.sql.
@RestController
@RequestMapping("/api/v1/admin/investment-result")
@RequiredArgsConstructor
public class AdminInvestmentResultController {

    private final InvestmentAnalysisBatchService investmentAnalysisBatchService;

    @PostMapping("/export")
    public ResponseEntity<InvestmentAnalysisBatchService.ExportResult> export() {
        return ResponseEntity.ok(investmentAnalysisBatchService.exportAnalysisCsv());
    }
}
