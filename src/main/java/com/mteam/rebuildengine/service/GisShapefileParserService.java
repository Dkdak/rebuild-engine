package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.utils.CoordinateReprojector;
import com.mteam.rebuildengine.utils.DbfReader;
import com.mteam.rebuildengine.utils.GeoUtils;
import com.mteam.rebuildengine.utils.ShpReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// GIS건물통합정보 Shapefile(.shp/.dbf) 파싱 -> CSV 출력 (F-14 데이터 이관). A0~A28 29개 필드 전부 보존한다
// (2026-07-26 확정 — 의미 불명 필드도 나중 재분석을 위해 버리지 않음, FEATURE_14 §3.2 참고).
// 695K건 규모라 JPA row-by-row 저장 대신, 건축물대장(F-13)과 동일하게 CSV로 뽑아 COPY로 적재하는 방식을 쓴다.
// 매일 새 날짜의 파일이 나올 수 있어(FEATURE_14 §3.1), shpBasePath를 매번 인자로 받는 재사용 가능한 배치로 만들었다.
@Service
public class GisShapefileParserService {

    private static final Logger logger = LogManager.getLogger(GisShapefileParserService.class);
    private static final Charset DBF_CHARSET = Charset.forName("MS949"); // CP949/UHC와 동일 코드페이지

    @Value("${data-migration.data-dir}")
    private String dataDir;

    public record ParseResult(int totalRows, int gisBuildingRows, int legalDongCodeRows) {
    }

    // zipFileName만 넘기면 관례대로 처리한다: {dataDir}/raw/{zipFileName}을 {dataDir}/raw/{확장자 뺀 파일명}에
    // 풀고, CSV는 {dataDir}/converted에 쓴다. 경로를 매번 다 안 적어도 되게 하기 위한 진입점(2026-07-26).
    public ParseResult extractZipAndParseByFileName(String zipFileName, int maxRows) throws IOException {
        String zipPath = dataDir + "/raw/" + zipFileName;
        String baseName = zipFileName.toLowerCase().endsWith(".zip")
                ? zipFileName.substring(0, zipFileName.length() - 4)
                : zipFileName;
        String extractDir = dataDir + "/raw/" + baseName;
        return extractZipAndParse(zipPath, extractDir, dataDir + "/converted", maxRows);
    }

    // zipPath(예: ".../raw/AL_D010_11_20260719.zip")를 extractDir에 압축 해제하고, 그 안에서 .shp를 찾아
    // parseAndExport를 그대로 호출한다. 매번 새 날짜 zip이 오는 워크플로(FEATURE_14 §3.1)를 위한 진입점.
    public ParseResult extractZipAndParse(String zipPath, String extractDir, String outputDir, int maxRows) throws IOException {
        Path extractPath = Path.of(extractDir);
        Files.createDirectories(extractPath);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(Path.of(zipPath)))) {
            Stream.generate(() -> nextEntry(zis))
                    .takeWhile(Objects::nonNull)
                    .forEach(entry -> extractEntry(zis, entry, extractPath));
        }
        logger.info("zip 압축 해제 완료: {} -> {}", zipPath, extractDir);

        Optional<Path> shpFile;
        try (var walk = Files.walk(extractPath)) {
            shpFile = walk.filter(p -> p.toString().toLowerCase().endsWith(".shp")).findFirst();
        }
        Path shp = shpFile.orElseThrow(() -> new IOException("압축 해제한 폴더에서 .shp 파일을 찾을 수 없습니다: " + extractDir));
        String shpBasePath = shp.toString().substring(0, shp.toString().length() - 4);

        return parseAndExport(shpBasePath, outputDir, maxRows);
    }

    // Stream.generate 람다 안에서 호출되므로 checked IOException을 밖으로 던질 수 없다 — Unchecked로 감싼다.
    private static ZipEntry nextEntry(ZipInputStream zis) {
        try {
            return zis.getNextEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void extractEntry(ZipInputStream zis, ZipEntry entry, Path extractPath) {
        try {
            Path target = extractPath.resolve(entry.getName()).normalize();
            if (!target.startsWith(extractPath)) {
                throw new IOException("zip 안에 상위 경로로 벗어나는 항목이 있습니다: " + entry.getName());
            }
            if (entry.isDirectory()) {
                Files.createDirectories(target);
                return;
            }
            Files.createDirectories(target.getParent());
            try (var out = new BufferedOutputStream(new FileOutputStream(target.toFile()))) {
                zis.transferTo(out);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // shpBasePath: 확장자 없는 경로 (예: ".../AL_D010_11_20260719/AL_D010_11_20260719")
    // outputDir: gis_building_export.csv, legal_dong_code_export.csv를 쓸 디렉토리
    // maxRows: 0 이하이면 전체 처리, 양수면 그 건수만 처리하고 중단(빠른 검증용)
    public ParseResult parseAndExport(String shpBasePath, String outputDir, int maxRows) throws IOException {
        CoordinateReprojector reprojector = new CoordinateReprojector();
        Map<String, Integer> partNoCounter = new HashMap<>();
        Set<String> seenBjdongCd = new HashSet<>();

        Path gisCsvPath = Path.of(outputDir, "gis_building_export.csv");
        Path dongCsvPath = Path.of(outputDir, "legal_dong_code_export.csv");

        ParseCounter counter = new ParseCounter();
        long limit = maxRows > 0 ? maxRows : Long.MAX_VALUE;

        try (DbfReader dbf = new DbfReader(shpBasePath + ".dbf", DBF_CHARSET);
             ShpReader shp = new ShpReader(shpBasePath + ".shp");
             BufferedWriter gisOut = Files.newBufferedWriter(gisCsvPath, StandardCharsets.UTF_8);
             BufferedWriter dongOut = Files.newBufferedWriter(dongCsvPath, StandardCharsets.UTF_8)) {

            writeGisHeader(gisOut);
            writeDongHeader(dongOut);

            Stream.generate(() -> nextPair(dbf, shp))
                    .takeWhile(Objects::nonNull)
                    .limit(limit)
                    .forEach(pair -> processRecord(pair, reprojector, partNoCounter, seenBjdongCd, gisOut, dongOut, counter));
        }

        ParseResult result = counter.toResult();
        logger.info("GIS 파싱 완료: 전체 {}건, gis_building {}건, legal_dong_code {}건",
                result.totalRows(), result.gisBuildingRows(), result.legalDongCodeRows());
        return result;
    }

    private record DbfShpPair(Map<String, String> attributes, ShpReader.Polygon polygon) {
    }

    // 집계 상태 객체 — total/gisRows/dongRows 지역 변수 증가 대신 여기로 분리(roles/backend.md §9).
    private static final class ParseCounter {
        private int total;
        private int gisRows;
        private int dongRows;

        ParseResult toResult() {
            return new ParseResult(total, gisRows, dongRows);
        }
    }

    // dbf/shp는 위치 기준으로 1:1 대응하는 레코드라 항상 같이 읽는다. dbf가 끝나면 null을 반환해
    // Stream.generate + takeWhile(Objects::nonNull)로 종료 조건을 표현할 수 있게 한다.
    private DbfShpPair nextPair(DbfReader dbf, ShpReader shp) {
        if (!dbf.hasNext()) {
            return null;
        }
        try {
            return new DbfShpPair(dbf.next(), shp.next());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void processRecord(DbfShpPair pair, CoordinateReprojector reprojector, Map<String, Integer> partNoCounter,
                                Set<String> seenBjdongCd, BufferedWriter gisOut, BufferedWriter dongOut,
                                ParseCounter counter) {
        counter.total++;
        if (counter.total % 100000 == 0) {
            logger.info("GIS 파싱 진행: {}건", counter.total);
        }

        Map<String, String> rec = pair.attributes();
        if (rec == null) {
            return; // 논리 삭제된 dbf 레코드
        }

        String pnu = rec.get("A2");
        if (pnu == null || pnu.length() != 19) {
            return; // PNU 형식이 아니면 매칭 불가능한 레코드라 스킵
        }

        String bjdongCd = rec.get("A3");
        String plotGbCd = pnu.substring(10, 11);
        String mnLotno = pnu.substring(11, 15);
        String subLotno = pnu.substring(15, 19);

        String address = rec.getOrDefault("A4", "");
        String[] parts = address.trim().split("\\s+");
        String sggNm = parts.length >= 2 ? parts[0] + " " + parts[1] : address;
        String bjdongNm = parts.length >= 3 ? String.join(" ", Arrays.copyOfRange(parts, 2, parts.length)) : "";

        String ufid = rec.getOrDefault("A21", "");
        String partKey = pnu + "|" + ufid;
        int partNo = partNoCounter.merge(partKey, 0, (a, b) -> a + 1);

        double[] centroidWgs84 = {0, 0};
        String geojson = null;
        ShpReader.Polygon polygon = pair.polygon();
        if (!polygon.rings().isEmpty() && !polygon.rings().get(0).isEmpty()) {
            double[] centroid5186 = GeoUtils.computeCentroid(polygon.rings().get(0));
            centroidWgs84 = reprojector.toWgs84(centroid5186[0], centroid5186[1]);

            List<List<double[]>> reprojectedRings = new ArrayList<>();
            for (List<double[]> ring : polygon.rings()) {
                List<double[]> reprojected = new ArrayList<>();
                for (double[] p : ring) {
                    reprojected.add(reprojector.toWgs84(p[0], p[1]));
                }
                reprojectedRings.add(reprojected);
            }
            geojson = GeoUtils.toGeoJsonPolygon(reprojectedRings);
        }

        writeGisRow(gisOut, pnu, ufid, partNo, rec, bjdongCd, sggNm, bjdongNm, plotGbCd, mnLotno, subLotno,
                centroidWgs84[1], centroidWgs84[0], geojson);
        counter.gisRows++;

        if (bjdongCd != null && !bjdongCd.isBlank() && !bjdongNm.isBlank() && seenBjdongCd.add(bjdongCd)) {
            writeDongRow(dongOut, bjdongCd, rec.get("A23"), sggNm, bjdongNm);
            counter.dongRows++;
        }
    }

    private void writeGisHeader(BufferedWriter w) throws IOException {
        w.write(String.join(",",
                "pnu", "building_ufid", "part_no", "raw_a0", "raw_a1", "sigungu_cd", "bjdong_cd", "sgg_nm", "bjdong_nm",
                "plot_gb_cd", "mn_lotno", "sub_lotno", "ledger_gb_nm", "raw_a8", "main_purpose_nm", "raw_a10",
                "structure_nm", "arch_area", "use_approval_date", "total_floor_area", "site_area", "height",
                "building_coverage_ratio", "floor_area_ratio", "raw_a19", "raw_a20", "centroid_lat", "centroid_lng",
                "polygon_geojson", "data_base_date", "raw_a24", "raw_a25", "sub_category_code", "raw_a27", "registered_date"));
        w.write("\n");
    }

    private void writeDongHeader(BufferedWriter w) throws IOException {
        w.write("bjdong_cd,sigungu_cd,sgg_nm,bjdong_nm\n");
    }

    // processRecord()가 Stream.generate 람다 안에서 호출하므로 checked IOException을 밖으로 못 던진다.
    private void writeGisRow(BufferedWriter w, String pnu, String ufid, int partNo, Map<String, String> rec,
                              String bjdongCd, String sggNm, String bjdongNm, String plotGbCd, String mnLotno,
                              String subLotno, double lat, double lng, String geojson) {
        try {
            w.write(String.join(",",
                    csv(pnu), csv(ufid), String.valueOf(partNo), csv(rec.get("A0")), csv(rec.get("A1")),
                    csv(rec.get("A23")), csv(bjdongCd), csv(sggNm), csv(bjdongNm), csv(plotGbCd), csv(mnLotno), csv(subLotno),
                    csv(rec.get("A7")), csv(rec.get("A8")), csv(rec.get("A9")), csv(rec.get("A10")), csv(rec.get("A11")),
                    csv(rec.get("A12")), csv(rec.get("A13")), csv(rec.get("A14")), csv(rec.get("A15")), csv(rec.get("A16")),
                    csv(rec.get("A17")), csv(rec.get("A18")), csv(rec.get("A19")), csv(rec.get("A20")),
                    String.valueOf(lat), String.valueOf(lng), csv(geojson), csv(rec.get("A22")),
                    csv(rec.get("A24")), csv(rec.get("A25")), csv(rec.get("A26")), csv(rec.get("A27")), csv(rec.get("A28"))));
            w.write("\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeDongRow(BufferedWriter w, String bjdongCd, String sigunguCd, String sggNm, String bjdongNm) {
        try {
            w.write(String.join(",", csv(bjdongCd), csv(sigunguCd), csv(sggNm), csv(bjdongNm)));
            w.write("\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
