package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.utils.PropertyType;
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
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// FEATURE_15_TRADE_DATA_MIGRATION.md §3.3 — rt.molit.go.kr 실거래가 매매 6종 원본 CSV(CP949, 15줄 메타데이터
// preamble, 유형마다 컬럼 스키마가 다름)를 통합 스키마(공통 필드만, §3.2 설계 결정) UTF-8 CSV 하나로 정규화한다.
// data/raw/trade/ 안의 파일을 이름 접두어로 자동 분류하므로, 월별 재다운로드 시 폴더에 파일만 추가하면
// 코드 변경 없이 재사용 가능하다(postgres/scripts/convert_trade_csv.py의 Java 재구현, 2026-08-06).
// 공식 거래 ID가 없어 행 내용만으로는 재현 불가능한 중복(같은 건물의 동일 조건 유닛이 같은 날 같은
// 가격에 다건 거래되는 경우 등)이 있어, 행 단위 중복방지 키 대신 원본 파일명(source_file)을 그대로
// 실어 보낸다 — load_trade_csv.sql이 파일 단위로 통째로 교체(DELETE+INSERT)한다.
@Service
public class TradeCsvConverterService {

    private static final Logger logger = LogManager.getLogger(TradeCsvConverterService.class);

    private static final int PREAMBLE_LINES = 15; // 헤더는 항상 16번째 줄(스킵 후 첫 레코드)
    private static final int BUFFER_SIZE = 1 << 16;
    private static final Pattern CANCEL_DATE_PATTERN = Pattern.compile("\\d{8}");

    // 원본 파일명 접두어("공장창고등"만 PropertyType.INDUSTRIAL.label()="공장창고"과 다름) -> 유형.
    private static final Map<String, PropertyType> FILE_PREFIX_TO_TYPE = new LinkedHashMap<>();
    static {
        FILE_PREFIX_TO_TYPE.put("아파트", PropertyType.APARTMENT);
        FILE_PREFIX_TO_TYPE.put("연립다세대", PropertyType.ROW_HOUSE);
        FILE_PREFIX_TO_TYPE.put("단독다가구", PropertyType.SINGLE_FAMILY);
        FILE_PREFIX_TO_TYPE.put("오피스텔", PropertyType.OFFICETEL);
        FILE_PREFIX_TO_TYPE.put("상업업무용", PropertyType.COMMERCIAL);
        FILE_PREFIX_TO_TYPE.put("공장창고등", PropertyType.INDUSTRIAL);
    }

    // 실측 확인된 원본 컬럼 인덱스(0-based) -> 통합 필드. complexNm/floor/shareType은 없는 유형이면 null.
    // shareType(지분구분)은 상업업무용/공장창고만 존재 — 지분매매(거래금액이 건물 전체가가 아님) 여부를
    // F-08이 걸러낼 수 있도록 보관(FEATURE.md §8.5 F-08 인계 블로커, 2026-08-07 추가).
    private record ColumnMap(int sgg, int lot, Integer complexNm, int area, int contractYm, int contractDay,
                              int price, Integer floor, int buildYear, int road, int cancelDate, int tradeType,
                              Integer shareType) {
    }

    private static final Map<PropertyType, ColumnMap> COLUMN_MAPS = Map.of(
            PropertyType.APARTMENT, new ColumnMap(1, 2, 5, 6, 7, 8, 9, 11, 14, 15, 16, 17, null),
            PropertyType.ROW_HOUSE, new ColumnMap(1, 2, 5, 6, 8, 9, 10, 11, 14, 15, 16, 17, null),
            PropertyType.SINGLE_FAMILY, new ColumnMap(1, 2, null, 5, 7, 8, 9, null, 12, 13, 14, 15, null),
            PropertyType.OFFICETEL, new ColumnMap(1, 2, 5, 6, 7, 8, 9, 10, 13, 14, 15, 16, null),
            PropertyType.COMMERCIAL, new ColumnMap(1, 3, null, 8, 14, 15, 10, 11, 17, 4, 18, 19, 16),
            PropertyType.INDUSTRIAL, new ColumnMap(1, 3, null, 8, 14, 15, 10, 11, 17, 4, 18, 19, 16)
    );

    private static final String[] OUTPUT_HEADER = {
            "property_type", "sgg_nm", "bjdong_nm", "lot_no", "complex_nm",
            "area_sqm", "floor", "price_10k_won", "build_year",
            "contract_date", "cancel_date", "trade_type_nm", "road_nm", "share_type", "source_file",
    };

    @Value("${data-migration.data-dir}")
    private String dataDir;

    public record ConvertResult(long totalRows, long cancelledRows, Map<String, Long> rowsByType) {
    }

    // typesFilter가 비어 있으면 전체 6종. 지정하면 그 유형의 원본 파일만 처리해 결과 CSV에 담는다 —
    // load_trade_csv.sql이 source_file 단위로 교체하므로, 지정한 유형의 파일만 다시 적재되고 나머지
    // 유형의 기존 행은 전혀 건드리지 않는다(예: share_type 컬럼 추가 후 상업업무용/공장창고 10개
    // 파일만 재적재, FEATURE.md §8.5).
    public ConvertResult convert(Set<PropertyType> typesFilter) throws IOException {
        File rawDir = new File(dataDir, "raw/trade");
        File[] rawFiles = rawDir.listFiles((dir, name) -> name.endsWith(".csv"));
        if (rawFiles == null || rawFiles.length == 0) {
            throw new IOException("실거래가 원본 CSV가 없습니다: " + rawDir.getAbsolutePath());
        }

        File outputFile = new File(dataDir, "converted/trade_utf8.csv");
        Files.createDirectories(outputFile.getParentFile().toPath());

        long total = 0;
        long cancelled = 0;
        Map<String, Long> rowsByType = new LinkedHashMap<>();

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(OUTPUT_HEADER).build())) {

            for (File rawFile : sortedByName(rawFiles)) {
                PropertyType type = classify(rawFile.getName());
                if (type == null) {
                    logger.warn("분류할 수 없는 파일 — 건너뜀: {}", rawFile.getName());
                    continue;
                }
                if (typesFilter != null && !typesFilter.isEmpty() && !typesFilter.contains(type)) {
                    continue;
                }
                ColumnMap cmap = COLUMN_MAPS.get(type);
                long typeRowCount = rowsByType.getOrDefault(type.label(), 0L);
                for (CSVRecord record : readDataRecords(rawFile)) {
                    Row row = toRow(type, cmap, record, rawFile.getName());
                    if (row == null) {
                        continue; // 면적/거래금액/계약일 중 하나라도 없으면 적재 후보 아님
                    }
                    printer.printRecord((Object[]) row.toCsvFields());
                    total++;
                    typeRowCount++;
                    if (row.cancelDate != null) {
                        cancelled++;
                    }
                }
                rowsByType.put(type.label(), typeRowCount);
            }
        }

        logger.info("실거래가 CSV 변환 완료: {} ({}건, 해제 {}건) -> {}", rawDir, total, cancelled, outputFile);
        return new ConvertResult(total, cancelled, rowsByType);
    }

    private static File[] sortedByName(File[] files) {
        File[] sorted = files.clone();
        Arrays.sort(sorted, Comparator.comparing(File::getName));
        return sorted;
    }

    private static PropertyType classify(String fileName) {
        return FILE_PREFIX_TO_TYPE.entrySet().stream()
                .filter(entry -> fileName.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    // CP949로 디코딩하며 15줄 preamble을 건너뛰고 헤더를 스킵한 나머지를 CSVRecord로 파싱한다.
    private static List<CSVRecord> readDataRecords(File file) throws IOException {
        Charset cp949 = Charset.forName("MS949");
        CharsetDecoder decoder = cp949.newDecoder()
                .onMalformedInput(CodingErrorAction.IGNORE)
                .onUnmappableCharacter(CodingErrorAction.IGNORE);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), decoder), BUFFER_SIZE)) {
            for (int i = 0; i < PREAMBLE_LINES; i++) {
                if (reader.readLine() == null) {
                    throw new IOException("파일이 preamble(15줄)보다 짧습니다: " + file.getName());
                }
            }
            CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
            try (CSVParser parser = CSVParser.parse(reader, format)) {
                return parser.getRecords();
            }
        }
    }

    private record Row(String propertyType, String sggNm, String bjdongNm, String lotNo, String complexNm,
                        BigDecimal areaSqm, Integer floor, BigDecimal price10kWon, Integer buildYear,
                        String contractDate, String cancelDate, String tradeTypeNm, String roadNm,
                        String shareType, String sourceFile) {
        String[] toCsvFields() {
            return new String[]{
                    propertyType, sggNm, bjdongNm, lotNo, complexNm,
                    areaSqm.toPlainString(), floor == null ? "" : floor.toString(), price10kWon.toPlainString(),
                    buildYear == null ? "" : buildYear.toString(),
                    contractDate, cancelDate == null ? "" : cancelDate, tradeTypeNm, roadNm,
                    shareType == null ? "" : shareType, sourceFile,
            };
        }
    }

    private static Row toRow(PropertyType type, ColumnMap cmap, CSVRecord record, String sourceFile) {
        if (record.size() <= maxIndex(cmap)) {
            return null;
        }
        String sggFull = get(record, cmap.sgg());
        String[] sggSplit = splitSggDong(sggFull);
        String lotNo = nullIfBlank(get(record, cmap.lot()));
        String complexNm = cmap.complexNm() == null ? null : nullIfBlank(get(record, cmap.complexNm()));
        BigDecimal areaSqm = parseNumber(get(record, cmap.area()));
        Integer floor = cmap.floor() == null ? null : parseInteger(get(record, cmap.floor()));
        BigDecimal price = parseNumber(get(record, cmap.price()));
        Integer buildYear = parseInteger(get(record, cmap.buildYear()));
        String contractDate = toContractDate(get(record, cmap.contractYm()), get(record, cmap.contractDay()));
        String cancelDate = toCancelDate(get(record, cmap.cancelDate()));
        String tradeTypeNm = nullIfBlank(get(record, cmap.tradeType()));
        String roadNm = nullIfBlank(get(record, cmap.road()));
        String shareType = cmap.shareType() == null ? null : nullIfBlank(get(record, cmap.shareType()));

        if (areaSqm == null || price == null || contractDate == null) {
            return null;
        }
        return new Row(type.label(), sggSplit[0], sggSplit[1], lotNo, complexNm,
                areaSqm, floor, price, buildYear, contractDate, cancelDate, tradeTypeNm, roadNm, shareType, sourceFile);
    }

    private static int maxIndex(ColumnMap cmap) {
        return Stream.of(cmap.sgg(), cmap.lot(), cmap.complexNm(), cmap.area(), cmap.contractYm(),
                        cmap.contractDay(), cmap.price(), cmap.floor(), cmap.buildYear(), cmap.road(),
                        cmap.cancelDate(), cmap.tradeType(), cmap.shareType())
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max().orElse(0);
    }

    private static String get(CSVRecord record, int index) {
        return record.get(index);
    }

    private static String[] splitSggDong(String value) {
        String trimmed = value == null ? "" : value.strip();
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace < 0) {
            return new String[]{trimmed, ""};
        }
        return new String[]{trimmed.substring(0, lastSpace), trimmed.substring(lastSpace + 1)};
    }

    private static String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }

    private static BigDecimal parseNumber(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInteger(String value) {
        BigDecimal number = parseNumber(value);
        return number == null ? null : number.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String v = value.strip().replace(",", "");
        return (v.isEmpty() || v.equals("-")) ? null : v;
    }

    private static String toContractDate(String ym, String day) {
        if (ym == null || day == null) {
            return null;
        }
        String ymTrim = ym.strip();
        String dayTrim = day.strip();
        if (ymTrim.length() != 6 || !ymTrim.chars().allMatch(Character::isDigit)) {
            return null;
        }
        String dayPadded = dayTrim.length() == 1 ? "0" + dayTrim : dayTrim;
        if (dayPadded.length() != 2 || !dayPadded.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return "%s-%s-%s".formatted(ymTrim.substring(0, 4), ymTrim.substring(4, 6), dayPadded);
    }

    private static String toCancelDate(String value) {
        String cleaned = clean(value);
        if (cleaned == null || !CANCEL_DATE_PATTERN.matcher(cleaned).matches()) {
            return null;
        }
        return "%s-%s-%s".formatted(cleaned.substring(0, 4), cleaned.substring(4, 6), cleaned.substring(6, 8));
    }
}
