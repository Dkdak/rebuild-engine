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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

// FEATURE_16_PRICE_DATA_MIGRATION.md §3.1~3.2 — 공동주택가격정보(data.go.kr, UTF-8, 전국 1,558만행,
// 15.58M rows)를 서울만 걸러 적재용 스키마로 정규화한다. 원본이 이미 UTF-8이라 인코딩 변환은 불필요하지만
// 전국 스캔이라 CSVParser의 Iterable을 그대로 순회(getRecords()로 통째 버퍼링하지 않음)해 메모리에 올리지
// 않는다 — trade(F-15)의 30개 개별 파일과 달리 이건 단일 대용량 파일이라 스트리밍이 필수.
@Service
public class ApartmentPriceCsvConverterService {

    private static final Logger logger = LogManager.getLogger(ApartmentPriceCsvConverterService.class);
    private static final String SEOUL = "서울특별시";
    private static final int BUFFER_SIZE = 1 << 16;

    // 원본 컬럼 인덱스(0-based, 실측 확인) — 기준연도,기준월,법정동코드,도로명주소,시도,시군구,읍면,동리,
    // 특수지코드,본번,부번,특수지명,단지명,동명,호명,전용면적,공시가격,단지코드,동코드,호코드,건축물대장PK
    private static final int COL_BASE_YEAR = 0;
    private static final int COL_BASE_MONTH = 1;
    private static final int COL_SIDO = 4;
    private static final int COL_SGG = 5;
    private static final int COL_DONG = 7;
    private static final int COL_MN_LOTNO = 9;
    private static final int COL_SUB_LOTNO = 10;
    private static final int COL_COMPLEX_NM = 12;
    private static final int COL_BUILDING_DONG_NM = 13;
    private static final int COL_UNIT_NM = 14;
    private static final int COL_AREA_SQM = 15;
    private static final int COL_PRICE = 16;

    private static final String[] OUTPUT_HEADER = {
            "sgg_nm", "bjdong_nm", "mn_lotno", "sub_lotno", "complex_nm", "building_dong_nm", "unit_nm",
            "area_sqm", "price", "base_year", "base_month",
    };

    @Value("${data-migration.data-dir}")
    private String dataDir;

    public record ConvertResult(long totalRows, long seoulRows) {
    }

    public ConvertResult convert() throws IOException {
        File rawFile = new File(dataDir, "raw/price/국토교통부_주택 공시가격 정보(2025).csv");
        File outputFile = new File(dataDir, "converted/apartment_price_utf8.csv");
        Files.createDirectories(outputFile.getParentFile().toPath());

        long total = 0;
        long seoul = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(rawFile), StandardCharsets.UTF_8), BUFFER_SIZE);
             Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(OUTPUT_HEADER).build())) {

            CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
            CSVParser parser = new CSVParser(reader, format);
            for (CSVRecord record : parser) {
                total++;
                if (!SEOUL.equals(record.get(COL_SIDO))) {
                    continue;
                }
                String[] row = toRow(record);
                if (row == null) {
                    continue;
                }
                printer.printRecord((Object[]) row);
                seoul++;
                if (total % 2_000_000 == 0) {
                    logger.info("공동주택가격정보 변환 진행: {}행 스캔, 서울 {}건", total, seoul);
                }
            }
        }

        logger.info("공동주택가격정보 변환 완료: 전체 {}행 -> 서울 {}건 -> {}", total, seoul, outputFile);
        return new ConvertResult(total, seoul);
    }

    private static String[] toRow(CSVRecord record) {
        String sggNm = record.get(COL_SIDO) + " " + record.get(COL_SGG);
        String bjdongNm = record.get(COL_DONG);
        String mnLotno = padLotno(record.get(COL_MN_LOTNO));
        String subLotno = padLotno(record.get(COL_SUB_LOTNO));
        String complexNm = nullIfBlank(record.get(COL_COMPLEX_NM));
        String buildingDongNm = nullIfBlank(record.get(COL_BUILDING_DONG_NM));
        String unitNm = nullIfBlank(record.get(COL_UNIT_NM));
        BigDecimal areaSqm = parseNumber(record.get(COL_AREA_SQM));
        BigDecimal price = parseNumber(record.get(COL_PRICE));
        String baseYear = record.get(COL_BASE_YEAR);
        String baseMonth = record.get(COL_BASE_MONTH);

        if (areaSqm == null || price == null || bjdongNm.isBlank()) {
            return null;
        }
        return new String[]{
                sggNm, bjdongNm, mnLotno, subLotno,
                complexNm == null ? "" : complexNm, buildingDongNm == null ? "" : buildingDongNm,
                unitNm == null ? "" : unitNm,
                areaSqm.toPlainString(), price.toPlainString(), baseYear, baseMonth,
        };
    }

    // building.mn_lotno/sub_lotno(F-13, load_building_csv.sql)가 4자리 0패딩이라 매칭 시 그대로 비교
    // 가능하도록 여기서 맞춘다 — FEATURE_16 §3.3.
    private static String padLotno(String value) {
        String v = value == null ? "0" : value.strip();
        if (v.isEmpty()) {
            v = "0";
        }
        return v.length() >= 4 ? v : "0".repeat(4 - v.length()) + v;
    }

    private static String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }

    private static BigDecimal parseNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
