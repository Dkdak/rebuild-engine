package com.mteam.rebuildengine.service.datapipeline;

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
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

// FEATURE_17_BUILDING_SUMMARY_MIGRATION.md §3.1~3.2 — 건축물대장 총괄표제부(서울, CP949, 6.5MB) 원본을
// 적재용 스키마로 정규화한다. 시군구코드명/법정동코드명이 이미 분리된 컬럼이고 주지번/부지번도 4자리
// 0패딩까지 원본에 반영돼 있어(실측 확인) trade(F-15)와 달리 주소 재조합·패딩 로직이 불필요하다.
// 원본 53개 컬럼 중 F-17이 명시한 소비 목적(단지 단위 집계 — 세대수/동수 등)에 필요한 것만 정규화
// (에너지효율등급·친환경인증 등 소비 기능 없는 컬럼은 드롭, permit과 같은 패턴). preamble 없음.
@Service
public class BuildingSummaryCsvConverterService {

    private static final Logger logger = LogManager.getLogger(BuildingSummaryCsvConverterService.class);
    private static final int BUFFER_SIZE = 1 << 16;

    // 원본 컬럼 인덱스(0-based, 실측 확인, 53개 컬럼 중 발췌) — 대지위치(0),시군구코드명(1),
    // 법정동코드명(2),...,주지번(4),부지번(5),...,대지면적(17),건축면적(18),...,연면적(20),...,
    // 용적률(22),...,주용도코드명(24),...,세대수(26),가구수(27),호수(28),승용승강기수(29),
    // 비상용승강기수(30),주건축물수(31),...,사용승인일자(45),...
    private static final int COL_SGG_NM = 1;
    private static final int COL_BJDONG_NM = 2;
    private static final int COL_MN_LOTNO = 4;
    private static final int COL_SUB_LOTNO = 5;
    private static final int COL_SITE_AREA_SQM = 17;
    private static final int COL_BUILDING_AREA_SQM = 18;
    private static final int COL_TOTAL_FLOOR_AREA_SQM = 20;
    private static final int COL_FLOOR_AREA_RATIO = 22;
    private static final int COL_MAIN_USE_NM = 24;
    private static final int COL_HOUSEHOLD_COUNT = 26;
    private static final int COL_UNIT_COUNT = 27;
    private static final int COL_ROOM_COUNT = 28;
    private static final int COL_ELEVATOR_PASSENGER_COUNT = 29;
    private static final int COL_ELEVATOR_EMERGENCY_COUNT = 30;
    private static final int COL_MAIN_BUILDING_COUNT = 31;
    private static final int COL_USE_APPROVAL_DATE = 45;

    private static final String[] OUTPUT_HEADER = {
            "sgg_nm", "bjdong_nm", "mn_lotno", "sub_lotno", "main_use_nm",
            "site_area_sqm", "building_area_sqm", "total_floor_area_sqm", "floor_area_ratio",
            "household_count", "unit_count", "room_count",
            "elevator_passenger_count", "elevator_emergency_count", "main_building_count",
            "use_approval_date",
    };

    @Value("${data-migration.data-dir}")
    private String dataDir;

    public record ConvertResult(long totalRows) {
    }

    public ConvertResult convert() throws IOException {
        File rawFile = new File(dataDir, "raw/building_summary/서울시 건축물대장 총괄표제부.csv");
        File outputFile = new File(dataDir, "converted/building_summary_utf8.csv");
        Files.createDirectories(outputFile.getParentFile().toPath());

        Charset cp949 = Charset.forName("MS949");
        CharsetDecoder decoder = cp949.newDecoder()
                .onMalformedInput(CodingErrorAction.IGNORE)
                .onUnmappableCharacter(CodingErrorAction.IGNORE);

        long total = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(rawFile), decoder), BUFFER_SIZE);
             Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(OUTPUT_HEADER).build())) {

            CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
            CSVParser parser = new CSVParser(reader, format);
            for (CSVRecord record : parser) {
                String[] row = toRow(record);
                if (row == null) {
                    continue;
                }
                printer.printRecord((Object[]) row);
                total++;
            }
        }

        logger.info("건축물대장 총괄표제부 변환 완료: {}건 -> {}", total, outputFile);
        return new ConvertResult(total);
    }

    private static String[] toRow(CSVRecord record) {
        String sggNm = record.get(COL_SGG_NM).strip();
        String bjdongNm = record.get(COL_BJDONG_NM).strip();
        if (sggNm.isBlank() || bjdongNm.isBlank()) {
            return null;
        }
        return new String[]{
                sggNm, bjdongNm, record.get(COL_MN_LOTNO).strip(), record.get(COL_SUB_LOTNO).strip(),
                record.get(COL_MAIN_USE_NM), record.get(COL_SITE_AREA_SQM), record.get(COL_BUILDING_AREA_SQM),
                record.get(COL_TOTAL_FLOOR_AREA_SQM), record.get(COL_FLOOR_AREA_RATIO),
                record.get(COL_HOUSEHOLD_COUNT), record.get(COL_UNIT_COUNT), record.get(COL_ROOM_COUNT),
                record.get(COL_ELEVATOR_PASSENGER_COUNT), record.get(COL_ELEVATOR_EMERGENCY_COUNT),
                record.get(COL_MAIN_BUILDING_COUNT), record.get(COL_USE_APPROVAL_DATE),
        };
    }
}
