package com.mteam.rebuildengine.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

// FEATURE_06_REMODELING.md §3.3 — 건축인허가정보(건축HUB, UTF-8 BOM) 원본 74개 컬럼 중 F-06에
// 필요한 것만 정규화(trade와 같은 패턴, JSONB로 전부 보존하지 않는다). 시군구/법정동/번/지가 이미
// 구조화된 컬럼이라(번/지는 4자리 0패딩까지 원본에 이미 반영돼 있음) trade(F-15)와 달리 주소
// 재조합·0패딩 로직이 불필요 — 시도+시군구만 합치면 building.sgg_cd_nm과 바로 비교 가능하다.
// 날짜 컬럼만 "20230801" -> "2023-08-01"로 정규화(safe_date SQL 함수 재사용을 위해).
@Service
public class PermitCsvConverterService {

    private static final Logger logger = LogManager.getLogger(PermitCsvConverterService.class);
    private static final int BUFFER_SIZE = 1 << 16;

    // 원본 컬럼 인덱스(0-based, 실측 확인, 74개 컬럼 중 발췌) —
    // PK,업무구분,시도,시군구,법정동,번,지,대지구분,건축구분,허가구분,허가번호,허가일,... ,착공예정일자(27),
    // 실제착공일자(28),사용승인구분(29),사용승인일자(30),...
    private static final int COL_SIDO = 2;
    private static final int COL_SGG = 3;
    private static final int COL_DONG = 4;
    private static final int COL_MN_LOTNO = 5;
    private static final int COL_SUB_LOTNO = 6;
    private static final int COL_BUILD_TYPE = 8;
    private static final int COL_PERMIT_TYPE = 9;
    private static final int COL_PERMIT_DATE = 11;
    private static final int COL_START_PLANNED_DATE = 27;
    private static final int COL_START_ACTUAL_DATE = 28;
    private static final int COL_USE_APPROVAL_TYPE = 29;
    private static final int COL_USE_APPROVAL_DATE = 30;

    private static final String[] OUTPUT_HEADER = {
            "sgg_nm", "bjdong_nm", "mn_lotno", "sub_lotno", "build_type", "permit_type",
            "permit_date", "start_planned_date", "start_actual_date",
            "use_approval_type", "use_approval_date", "source_file",
    };

    @Value("${data-migration.data-dir}")
    private String dataDir;

    public record ConvertResult(long totalRows, String sourceFile) {
    }

    // rawFileName 예: 인허가_20260801054906.csv (postgres/data/raw/permit/ 기준 상대 파일명)
    public ConvertResult convert(String rawFileName) throws IOException {
        File rawFile = new File(dataDir, "raw/permit/" + rawFileName);
        File outputFile = new File(dataDir, "converted/permit_utf8.csv");
        Files.createDirectories(outputFile.getParentFile().toPath());

        long total = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(rawFile), StandardCharsets.UTF_8), BUFFER_SIZE);
             Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(OUTPUT_HEADER).build())) {

            CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
            CSVParser parser = new CSVParser(reader, format);
            for (CSVRecord record : parser) {
                String[] row = toRow(record, rawFileName);
                if (row == null) {
                    continue;
                }
                printer.printRecord((Object[]) row);
                total++;
            }
        }

        logger.info("건축인허가정보 변환 완료: {}건 -> {}", total, outputFile);
        return new ConvertResult(total, rawFileName);
    }

    private static String[] toRow(CSVRecord record, String sourceFile) {
        String sido = record.get(COL_SIDO).strip();
        String sgg = record.get(COL_SGG).strip();
        String dong = record.get(COL_DONG).strip();
        if (sido.isBlank() || sgg.isBlank() || dong.isBlank()) {
            return null;
        }
        return new String[]{
                sido + " " + sgg, dong,
                record.get(COL_MN_LOTNO).strip(), record.get(COL_SUB_LOTNO).strip(),
                record.get(COL_BUILD_TYPE), record.get(COL_PERMIT_TYPE),
                toIsoDate(record.get(COL_PERMIT_DATE)), toIsoDate(record.get(COL_START_PLANNED_DATE)),
                toIsoDate(record.get(COL_START_ACTUAL_DATE)), record.get(COL_USE_APPROVAL_TYPE),
                toIsoDate(record.get(COL_USE_APPROVAL_DATE)), sourceFile,
        };
    }

    // "20230801" -> "2023-08-01". 미착공 등으로 비어있는 값은 그대로 빈 문자열(safe_date가 NULL 처리).
    private static String toIsoDate(String value) {
        String trimmed = value == null ? "" : value.strip();
        if (!trimmed.matches("\\d{8}")) {
            return "";
        }
        return trimmed.substring(0, 4) + "-" + trimmed.substring(4, 6) + "-" + trimmed.substring(6, 8);
    }
}
