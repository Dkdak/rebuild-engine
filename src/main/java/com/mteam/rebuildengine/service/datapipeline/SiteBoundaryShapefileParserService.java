package com.mteam.rebuildengine.service.datapipeline;

import com.mteam.rebuildengine.utils.CoordinateReprojector;
import com.mteam.rebuildengine.utils.DbfReader;
import com.mteam.rebuildengine.utils.GeoUtils;
import com.mteam.rebuildengine.utils.ShpReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

// 연속지적도(필지 경계) Shapefile 파싱 -> CSV 출력 (FEATURE_05_PROPERTY_INFO.md §5.1 site_boundary).
// GisShapefileParserService(F-14 건물통합정보)와 같은 저수준 리더(ShpReader/DbfReader)·좌표 재투영
// (CoordinateReprojector, .prj 확인 결과 EPSG:5186으로 동일)·GeoJSON 변환(GeoUtils)을 그대로 재사용하고,
// 필드 매핑만 이 데이터셋 스키마(A0~A7, "AL_D002" 계열)에 맞게 새로 만든다 — 두 파서가 저수준 유틸을
// 공유하되 스키마 해석 로직은 독립적으로 유지(F-14 스키마가 바뀌어도 이쪽엔 영향 없음).
// 필드 매핑(2026-08-09 실측, pyshp로 직접 확인): A0=원본ID(안 씀), A1=PNU(19자), A2=법정동코드(10자),
// A3=전체주소, A4=지번(본번-부번), A5=지번+"번지"라벨(A4와 중복이라 안 씀), A6=갱신일자(YYYYMMDD),
// A7=시군구코드(5자).
@Service
public class SiteBoundaryShapefileParserService {

    private static final Logger logger = LogManager.getLogger(SiteBoundaryShapefileParserService.class);
    private static final Charset DBF_CHARSET = Charset.forName("MS949");

    public record ParseResult(int totalRows, int siteBoundaryRows) {
    }

    // shpBasePath: 확장자 없는 경로(예: ".../site_boundary/AL_D002_11_20260719")
    // outputDir: site_boundary_export.csv를 쓸 디렉토리. maxRows<=0이면 전체 처리.
    public ParseResult parseAndExport(String shpBasePath, String outputDir, int maxRows) throws IOException {
        CoordinateReprojector reprojector = new CoordinateReprojector();
        Path csvPath = Path.of(outputDir, "site_boundary_export.csv");
        ParseCounter counter = new ParseCounter();
        long limit = maxRows > 0 ? maxRows : Long.MAX_VALUE;

        try (DbfReader dbf = new DbfReader(shpBasePath + ".dbf", DBF_CHARSET);
             ShpReader shp = new ShpReader(shpBasePath + ".shp");
             BufferedWriter out = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {

            writeHeader(out);
            Stream.generate(() -> nextPair(dbf, shp))
                    .takeWhile(Objects::nonNull)
                    .limit(limit)
                    .forEach(pair -> processRecord(pair, reprojector, out, counter));
        }

        ParseResult result = counter.toResult();
        logger.info("연속지적도 파싱 완료: 전체 {}건, site_boundary {}건", result.totalRows(), result.siteBoundaryRows());
        return result;
    }

    private record DbfShpPair(Map<String, String> attributes, ShpReader.Polygon polygon) {
    }

    private static final class ParseCounter {
        private int total;
        private int rows;

        ParseResult toResult() {
            return new ParseResult(total, rows);
        }
    }

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

    private void processRecord(DbfShpPair pair, CoordinateReprojector reprojector, BufferedWriter out, ParseCounter counter) {
        counter.total++;
        if (counter.total % 100000 == 0) {
            logger.info("연속지적도 파싱 진행: {}건", counter.total);
        }

        Map<String, String> rec = pair.attributes();
        if (rec == null) {
            return; // 논리 삭제된 dbf 레코드
        }

        String pnu = rec.get("A1");
        if (pnu == null || pnu.length() != 19) {
            return; // PNU 형식이 아니면 매칭 불가능한 레코드라 스킵(GisShapefileParserService와 동일 원칙)
        }

        String geojson = toGeoJson(pair.polygon(), reprojector);

        writeRow(out, pnu, rec.get("A2"), rec.get("A7"), rec.get("A4"), rec.get("A3"), geojson, rec.get("A6"));
        counter.rows++;
    }

    private String toGeoJson(ShpReader.Polygon polygon, CoordinateReprojector reprojector) {
        if (polygon.rings().isEmpty() || polygon.rings().get(0).isEmpty()) {
            return null;
        }
        List<List<double[]>> reprojectedRings = new ArrayList<>();
        for (List<double[]> ring : polygon.rings()) {
            List<double[]> reprojected = new ArrayList<>();
            for (double[] p : ring) {
                reprojected.add(reprojector.toWgs84(p[0], p[1]));
            }
            reprojectedRings.add(reprojected);
        }
        return GeoUtils.toGeoJsonPolygon(reprojectedRings);
    }

    private void writeHeader(BufferedWriter w) throws IOException {
        w.write("pnu,bjdong_cd,sigungu_cd,lot_address,full_address,polygon_geojson,data_base_date\n");
    }

    // processRecord()가 Stream.generate 람다 안에서 호출하므로 checked IOException을 밖으로 못 던진다
    // (GisShapefileParserService.writeGisRow와 동일한 이유).
    private void writeRow(BufferedWriter w, String pnu, String bjdongCd, String sigunguCd, String lotAddress,
                           String fullAddress, String geojson, String rawDate) {
        try {
            w.write(String.join(",",
                    csv(pnu), csv(bjdongCd), csv(sigunguCd), csv(lotAddress), csv(fullAddress), csv(geojson),
                    csv(toIsoDate(rawDate))));
            w.write("\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // DBF Date 필드(YYYYMMDD 8자리)를 ISO(YYYY-MM-DD)로 변환 — load 스크립트의 safe_date()가 그 형식을 기대.
    private static String toIsoDate(String raw) {
        if (raw == null || raw.length() != 8) {
            return null;
        }
        return raw.substring(0, 4) + "-" + raw.substring(4, 6) + "-" + raw.substring(6, 8);
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
