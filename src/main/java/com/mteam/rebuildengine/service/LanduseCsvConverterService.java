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
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;

// FEATURE_06_REMODELING.md §3.2-2/§3.3 — 토지이용계획정보(V-WORLD, CP949, 서울, AL_D155_11) 원본
// 10,332,739행 중 "용도지역지구명"이 법정 16개 용도지역 중 하나와 정확히 일치하는 행만 걸러
// landuse 적재용 스키마로 정규화한다. 지구단위계획구역·교육환경보호구역 등 다른 종류의 중첩 지정
// 행은 버린다(실측 확인, F-06 §3.2-2 "필터링 주의"). preamble 없음.
@Service
public class LanduseCsvConverterService {

    private static final Logger logger = LogManager.getLogger(LanduseCsvConverterService.class);
    private static final int BUFFER_SIZE = 1 << 16;
    private static final long LOG_INTERVAL = 2_000_000L;

    // 원본 컬럼 인덱스(0-based, 실측 확인) — 고유번호,법정동코드,법정동명,대장구분코드,대장구분명,
    // 지번,도면번호,저촉여부코드,저촉여부,용도지역지구코드,용도지역지구명,등록일자,데이터기준일자,
    // 원천시도시군구코드,비고내용
    private static final int COL_BJDONG_NM = 2;
    private static final int COL_LOT_NO = 5;
    private static final int COL_ZONE_NAME = 10;

    // 서울특별시 도시계획 조례 제44조·제48조 상한표(F-06 §3.2-2)와 정확히 같은 16개 — zoning_limit
    // 테이블의 zone_name과 FK로 묶인다(seed_zoning_limit.sql 선행 필요).
    private static final Set<String> VALID_ZONE_NAMES = Set.of(
            "제1종전용주거지역", "제2종전용주거지역", "제1종일반주거지역", "제2종일반주거지역", "제3종일반주거지역",
            "준주거지역", "중심상업지역", "일반상업지역", "근린상업지역", "유통상업지역",
            "전용공업지역", "일반공업지역", "준공업지역", "보전녹지지역", "생산녹지지역", "자연녹지지역"
    );

    private static final String[] OUTPUT_HEADER = {"sgg_nm", "bjdong_nm", "mn_lotno", "sub_lotno", "zone_name"};

    @Value("${data-migration.data-dir}")
    private String dataDir;

    public record ConvertResult(long totalRows, long validZoneRows) {
    }

    public ConvertResult convert() throws IOException {
        File rawFile = new File(dataDir, "raw/landuse/AL_D155_11_20260711.csv");
        File outputFile = new File(dataDir, "converted/landuse_utf8.csv");
        Files.createDirectories(outputFile.getParentFile().toPath());

        Charset cp949 = Charset.forName("MS949");
        CharsetDecoder decoder = cp949.newDecoder()
                .onMalformedInput(CodingErrorAction.IGNORE)
                .onUnmappableCharacter(CodingErrorAction.IGNORE);

        long total = 0;
        long validZone = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(rawFile), decoder), BUFFER_SIZE);
             Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(OUTPUT_HEADER).build())) {

            CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
            CSVParser parser = new CSVParser(reader, format);
            for (CSVRecord record : parser) {
                total++;
                String[] row = toRow(record);
                if (row != null) {
                    printer.printRecord((Object[]) row);
                    validZone++;
                }
                if (total % LOG_INTERVAL == 0) {
                    logger.info("토지이용계획정보 변환 진행: {}행 처리, {}건 유효 용도지역", total, validZone);
                }
            }
        }

        logger.info("토지이용계획정보 변환 완료: 전체 {}행 중 유효 용도지역 {}건 -> {}", total, validZone, outputFile);
        return new ConvertResult(total, validZone);
    }

    private static String[] toRow(CSVRecord record) {
        String zoneName = record.get(COL_ZONE_NAME).strip();
        if (!VALID_ZONE_NAMES.contains(zoneName)) {
            return null;
        }
        String[] sggDong = splitSggDong(record.get(COL_BJDONG_NM));
        String[] lot = splitLotNo(record.get(COL_LOT_NO));
        if (sggDong[1].isBlank()) {
            return null;
        }
        return new String[]{sggDong[0], sggDong[1], lot[0], lot[1], zoneName};
    }

    // "서울특별시 종로구 평창동" -> ["서울특별시 종로구", "평창동"] — land_price(F-16)의 시군구 분리와 동일 방식.
    private static String[] splitSggDong(String value) {
        String trimmed = value == null ? "" : value.strip();
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace < 0) {
            return new String[]{trimmed, ""};
        }
        return new String[]{trimmed.substring(0, lastSpace), trimmed.substring(lastSpace + 1)};
    }

    // "473-1" -> ["0473", "0001"], "591"(부번 없음) -> ["0591", "0000"]. building.mn_lotno/sub_lotno
    // (F-13)가 4자리 0패딩이라 여기서 맞춘다(land_price와 동일 방식, FEATURE_16 §3.3).
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
}
