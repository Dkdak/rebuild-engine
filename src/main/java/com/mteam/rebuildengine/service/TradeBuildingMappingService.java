package com.mteam.rebuildengine.service;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// FEATURE_15_TRADE_DATA_MIGRATION.md §3.4 — trade<->building 매칭 배치. GIS 매칭(building_gis_mapping,
// 1:1이라 PNU 키 기반 Java 인메모리 매칭)과 달리 trade는 건물 1개당 거래가 여러 건 붙는 N:1 관계라
// 별도 매핑 테이블 대신 trade.building_id 컬럼 하나로 충분하고, 매칭 알고리즘도 주소 텍스트 완전일치
// + pg_trgm 유사도(F-13 §3.7에 이미 설치) 폴백이라 인덱스를 활용할 수 있는 SQL로 계산하는 게 자연스럽다.
// DB에는 직접 쓰지 않고 CSV만 만든다 — 실제 반영은 postgres/sql/load_trade_building_mapping.sql
// (GIS 매칭과 같은 export -> psql load 흐름 재사용).
@Service
@RequiredArgsConstructor
public class TradeBuildingMappingService {

    private static final Logger logger = LogManager.getLogger(TradeBuildingMappingService.class);

    // pg_trgm 기본 유사도 임계값(0.3)보다 다소 보수적으로 잡았다 — 같은 시군구+법정동으로 먼저 좁힌
    // 뒤에만 적용하지만, 그래도 너무 낮으면 엉뚱한 건물에 잘못 매칭될 수 있어서.
    private static final double SIMILARITY_THRESHOLD = 0.4;

    private final JdbcTemplate jdbcTemplate;

    @Value("${data-migration.data-dir}")
    private String dataDir;

    public record MatchResult(long total, long exactMatch, long fuzzyMatch, long noMatch) {
    }

    @Transactional
    public MatchResult exportMatchingCsv() {
        long total = count("SELECT count(*) FROM trade");

        jdbcTemplate.execute("DROP TABLE IF EXISTS trade_exact_match");
        jdbcTemplate.execute("""
                CREATE TEMP TABLE trade_exact_match AS
                SELECT DISTINCT ON (t.id) t.id AS trade_id, b.bdrg_sn AS building_id
                FROM trade t
                JOIN building b ON b.is_deleted = false
                    AND b.plat_plc = t.sgg_nm || ' ' || t.bjdong_nm || ' ' || t.lot_no
                WHERE t.lot_no IS NOT NULL
                ORDER BY t.id, b.bdrg_sn
                """);

        Path outputPath = Path.of(dataDir, "converted", "trade_building_mapping_export.csv");
        long[] counters = new long[2]; // [0]=exact, [1]=fuzzy
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("trade_id,building_id,match_type");
            writer.newLine();

            jdbcTemplate.query("SELECT trade_id, building_id FROM trade_exact_match", rs -> {
                writeRow(writer, rs.getLong("trade_id"), rs.getString("building_id"), "EXACT");
                counters[0]++;
            });

            // 지번이 "6*"/"1***"처럼 마스킹된 행(상업업무용·공장창고 다수)은 fuzzy 매칭에서 제외한다 —
            // 마스킹된 부분 번지로 유사도를 매기면 엉뚱한 건물에 그럴듯하게 매칭될 위험이 커서, 이런
            // 행은 완전일치 실패 시 그냥 매칭 없음으로 남긴다(§3.4 "매칭 실패 허용").
            String fuzzySql = """
                    SELECT t.id AS trade_id, best.bdrg_sn AS building_id
                    FROM trade t
                    CROSS JOIN LATERAL (
                        SELECT b.bdrg_sn
                        FROM building b
                        WHERE b.is_deleted = false
                          AND b.sgg_cd_nm = t.sgg_nm
                          AND b.stdg_cd_nm = t.bjdong_nm
                          AND similarity(b.plat_plc, t.sgg_nm || ' ' || t.bjdong_nm || ' ' || t.lot_no) > ?
                        ORDER BY similarity(b.plat_plc, t.sgg_nm || ' ' || t.bjdong_nm || ' ' || t.lot_no) DESC
                        LIMIT 1
                    ) best
                    WHERE t.lot_no IS NOT NULL
                      AND t.lot_no NOT LIKE '%*%'
                      AND NOT EXISTS (SELECT 1 FROM trade_exact_match m WHERE m.trade_id = t.id)
                    """;
            jdbcTemplate.query(fuzzySql, rs -> {
                writeRow(writer, rs.getLong("trade_id"), rs.getString("building_id"), "FUZZY");
                counters[1]++;
            }, SIMILARITY_THRESHOLD);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            jdbcTemplate.execute("DROP TABLE IF EXISTS trade_exact_match");
        }

        MatchResult result = new MatchResult(total, counters[0], counters[1], total - counters[0] - counters[1]);
        logger.info("trade-building 매칭 CSV 출력 완료: {} (전체 {}건, EXACT {}, FUZZY {}, NO_MATCH {})",
                outputPath, result.total(), result.exactMatch(), result.fuzzyMatch(), result.noMatch());
        return result;
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private void writeRow(BufferedWriter writer, long tradeId, String buildingId, String matchType) {
        try {
            writer.write(tradeId + "," + buildingId + "," + matchType);
            writer.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
