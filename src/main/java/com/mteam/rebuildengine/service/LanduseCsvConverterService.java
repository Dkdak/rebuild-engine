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
// 10,332,739행을 한 번 스캔하면서 "용도지역지구명"이 법정 16개 용도지역과 정확히 일치하는 행은
// landuse_utf8.csv(용도지역, 스코어링 입력)로 쓴다. 나머지 행은 처음엔 전부 landuse_district_utf8.csv
// (지구/구역 지정, §2.1 판단 근거 표시 전용)로 썼으나, 실측 결과 92%(919만행)가 "과밀억제권역"·
// "도시지역"처럼 서울 전역/광역에 거의 균일하게 붙는 값이라 건물 간 변별력이 없었다(2026-08-08).
// 그래서 리모델링 가능성 판단과 실제 관련 있는 항목(MEANINGFUL_DISTRICT_NAMES)만 필터링해서 쓴다
// — 파일이 커서(1000만 행+) 두 번 스캔하는 대신 한 번에 두 출력을 만든다. preamble 없음.
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

    // 리모델링 가능성 판단과 실제 관련 있는 지구/구역 지정만 유지(2026-08-08 실측 후 선정) — 제외한
    // 값(토지거래계약에관한허가구역·과밀억제권역·도시지역·가축사육제한구역·대공방어협조구역·상대/절대
    // 보호구역 등)은 서울 전역/광역 단위로 거의 모든 필지에 동일하게 붙어 건물 간 변별력이 없었다.
    private static final Set<String> MEANINGFUL_DISTRICT_NAMES = Set.of(
            "지구단위계획구역", "정비구역", "재정비촉진지구", "개발제한구역", "고도지구",
            "역사문화환경보존지역", "중점경관관리구역", "가로구역별 최고높이 제한지역", "건축허가·착공제한지역",
            "서울도심", "문화유산", "국가지정문화유산구역", "공장설립제한지역", "공장설립승인지역",
            "(한강)폐기물매립시설 설치제한지역"
    );

    private static final String[] ZONE_OUTPUT_HEADER = {"sgg_nm", "bjdong_nm", "mn_lotno", "sub_lotno", "zone_name"};
    private static final String[] DISTRICT_OUTPUT_HEADER =
            {"sgg_nm", "bjdong_nm", "mn_lotno", "sub_lotno", "district_name"};

    @Value("${data-migration.data-dir}")
    private String dataDir;

    public record ConvertResult(long totalRows, long validZoneRows, long districtRows) {
    }

    public ConvertResult convert() throws IOException {
        File rawFile = new File(dataDir, "raw/landuse/AL_D155_11_20260711.csv");
        File zoneOutputFile = new File(dataDir, "converted/landuse_utf8.csv");
        File districtOutputFile = new File(dataDir, "converted/landuse_district_utf8.csv");
        Files.createDirectories(zoneOutputFile.getParentFile().toPath());

        Charset cp949 = Charset.forName("MS949");
        CharsetDecoder decoder = cp949.newDecoder()
                .onMalformedInput(CodingErrorAction.IGNORE)
                .onUnmappableCharacter(CodingErrorAction.IGNORE);

        long total = 0;
        long validZone = 0;
        long district = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(rawFile), decoder), BUFFER_SIZE);
             Writer zoneWriter = new OutputStreamWriter(new FileOutputStream(zoneOutputFile), StandardCharsets.UTF_8);
             Writer districtWriter = new OutputStreamWriter(new FileOutputStream(districtOutputFile), StandardCharsets.UTF_8);
             CSVPrinter zonePrinter = new CSVPrinter(zoneWriter, CSVFormat.DEFAULT.builder().setHeader(ZONE_OUTPUT_HEADER).build());
             CSVPrinter districtPrinter = new CSVPrinter(districtWriter, CSVFormat.DEFAULT.builder().setHeader(DISTRICT_OUTPUT_HEADER).build())) {

            CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
            CSVParser parser = new CSVParser(reader, format);
            for (CSVRecord record : parser) {
                total++;
                String[] sggDong = splitSggDong(record.get(COL_BJDONG_NM));
                if (!sggDong[1].isBlank()) {
                    String designationName = record.get(COL_ZONE_NAME).strip();
                    String[] lot = splitLotNo(record.get(COL_LOT_NO));
                    if (VALID_ZONE_NAMES.contains(designationName)) {
                        zonePrinter.printRecord((Object[]) new String[]{sggDong[0], sggDong[1], lot[0], lot[1], designationName});
                        validZone++;
                    } else if (MEANINGFUL_DISTRICT_NAMES.contains(designationName)) {
                        districtPrinter.printRecord((Object[]) new String[]{sggDong[0], sggDong[1], lot[0], lot[1], designationName});
                        district++;
                    }
                }
                if (total % LOG_INTERVAL == 0) {
                    logger.info("토지이용계획정보 변환 진행: {}행 처리, {}건 유효 용도지역, {}건 지구/구역", total, validZone, district);
                }
            }
        }

        logger.info("토지이용계획정보 변환 완료: 전체 {}행 중 용도지역 {}건 -> {}, 지구/구역 {}건 -> {}",
                total, validZone, zoneOutputFile, district, districtOutputFile);
        return new ConvertResult(total, validZone, district);
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
