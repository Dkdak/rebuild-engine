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
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

// FEATURE_16_PRICE_DATA_MIGRATION.md §3.1~3.2 — 개별공시지가정보(V-WORLD, CP949, 서울만 102MB)를
// 적재용 스키마로 정규화한다. preamble 없음(원본 첫 줄이 바로 헤더) — trade(F-15)와 달리 이미 서울만이라
// 필터링도 불필요.
@Service
public class LandPriceCsvConverterService {

    private static final Logger logger = LogManager.getLogger(LandPriceCsvConverterService.class);
    private static final int BUFFER_SIZE = 1 << 16;

    // 원본 컬럼 인덱스(0-based, 실측 확인) — 고유번호,법정동코드,법정동명,특수지구분코드,특수지구분명,
    // 지번,기준연도,기준월,공시지가,공시일자,표준지여부,데이터기준일자,원천시도시군구코드
    private static final int COL_BJDONG_NM = 2;
    private static final int COL_LOT_NO = 5;
    private static final int COL_BASE_YEAR = 6;
    private static final int COL_BASE_MONTH = 7;
    private static final int COL_PRICE = 8;

    private static final String[] OUTPUT_HEADER = {
            "sgg_nm", "bjdong_nm", "mn_lotno", "sub_lotno", "price", "base_year", "base_month",
    };

    @Value("${data-migration.data-dir}")
    private String dataDir;

    public record ConvertResult(long totalRows) {
    }

    public ConvertResult convert() throws IOException {
        File rawFile = new File(dataDir, "raw/price/AL_D151_11_20260526.csv");
        File outputFile = new File(dataDir, "converted/land_price_utf8.csv");
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

        logger.info("개별공시지가정보 변환 완료: {}건 -> {}", total, outputFile);
        return new ConvertResult(total);
    }

    private static String[] toRow(CSVRecord record) {
        String[] sggDong = splitSggDong(record.get(COL_BJDONG_NM));
        String[] lot = splitLotNo(record.get(COL_LOT_NO));
        BigDecimal price = parseNumber(record.get(COL_PRICE));
        String baseYear = record.get(COL_BASE_YEAR);
        String baseMonth = record.get(COL_BASE_MONTH);

        if (sggDong[1].isBlank() || price == null) {
            return null;
        }
        return new String[]{sggDong[0], sggDong[1], lot[0], lot[1], price.toPlainString(), baseYear, baseMonth};
    }

    // "서울특별시 강서구 공항동" -> ["서울특별시 강서구", "공항동"] — trade(F-15)의 시군구 분리와 동일 방식.
    private static String[] splitSggDong(String value) {
        String trimmed = value == null ? "" : value.strip();
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace < 0) {
            return new String[]{trimmed, ""};
        }
        return new String[]{trimmed.substring(0, lastSpace), trimmed.substring(lastSpace + 1)};
    }

    // "14-162" -> ["0014", "0162"], "591"(부번 없음) -> ["0591", "0000"]. building.mn_lotno/sub_lotno
    // (F-13)가 4자리 0패딩이라 여기서 맞춘다(FEATURE_16 §3.3).
    private static String[] splitLotNo(String value) {
        String trimmed = value == null ? "" : value.strip();
        int dash = trimmed.indexOf('-');
        String mn = dash < 0 ? trimmed : trimmed.substring(0, dash);
        String sub = dash < 0 ? "0" : trimmed.substring(dash + 1);
        return new String[]{padLotno(mn), padLotno(sub)};
    }

    private static String padLotno(String value) {
        String v = value == null || value.isBlank() ? "0" : value.strip();
        return v.length() >= 4 ? v : "0".repeat(4 - v.length()) + v;
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
