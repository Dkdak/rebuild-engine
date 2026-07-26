package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.service.CsvEncodingConverterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

// 공공데이터 원본 파일 가공용 범용 유틸리티 (F-13 이후 반복되는 CSV 인코딩 문제 등을 매번 수동으로
// 처리하지 않도록 재사용 가능하게 API로 노출, 2026-07-26)
@RestController
@RequestMapping("/api/v1/admin/data-tools")
@RequiredArgsConstructor
public class AdminDataToolsController {

    private final CsvEncodingConverterService csvEncodingConverterService;

    // sourceCharset 예: MS949(CP949/UHC), EUC-KR 등. 출력은 항상 UTF-8.
    @PostMapping("/csv-to-utf8")
    public ResponseEntity<CsvEncodingConverterService.ConvertResult> convertCsvToUtf8(
            @RequestParam String sourcePath,
            @RequestParam String sourceCharset,
            @RequestParam String targetPath
    ) throws IOException {
        return ResponseEntity.ok(csvEncodingConverterService.convertToUtf8(sourcePath, sourceCharset, targetPath));
    }

    // 관례 경로만 쓰는 단순 버전 — postgres/data/raw/{rawFileName} -> postgres/data/converted/{outputFileName}
    @PostMapping("/csv-to-utf8-simple")
    public ResponseEntity<CsvEncodingConverterService.ConvertResult> convertCsvToUtf8Simple(
            @RequestParam String rawFileName,
            @RequestParam String sourceCharset,
            @RequestParam String outputFileName
    ) throws IOException {
        return ResponseEntity.ok(csvEncodingConverterService.convertRawToUtf8(rawFileName, sourceCharset, outputFileName));
    }
}
