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

// FEATURE_16_PRICE_DATA_MIGRATION.md §3.3 — apartment_price/land_price로 시작한 "시군구+법정동+
// 본번+부번(이미 구조화된 컬럼, 지번 마스킹 없음)" 매칭 패턴을 landuse/permit(F-06)에 이어
// building_summary(F-17)에도 그대로 적용한다 — 다섯 테이블 전부 같은 모양의 컬럼(sgg_nm/bjdong_nm/
// mn_lotno/sub_lotno)을 갖도록 설계해 매칭 SQL 자체를 그대로 재사용(2026-08-08). trade(F-15)는
// 지번 마스킹이 섞여 있어 주소 문자열 재조합+전면 pg_trgm 폴백이 필요해 알고리즘 자체가 달라 여기
// 포함하지 않는다(FEATURE_16 §3.3 backend 판단, 계속 유효). DB에는 직접 안 쓰고 CSV만 만든다.
@Service
@RequiredArgsConstructor
public class AddressBuildingMappingService {

    private static final Logger logger = LogManager.getLogger(AddressBuildingMappingService.class);
    // 법정동명 철자 편차(오탈자 등)만 흡수하는 용도라 지번은 그대로 정확일치 요구 — trade보다 훨씬 좁은
    // 폴백 범위.
    private static final double SIMILARITY_THRESHOLD = 0.4;

    public enum AddressTable {
        APARTMENT_PRICE("apartment_price"), LAND_PRICE("land_price"), LANDUSE("landuse"), PERMIT("permit"),
        BUILDING_SUMMARY("building_summary");

        private final String tableName;

        AddressTable(String tableName) {
            this.tableName = tableName;
        }
    }

    private final JdbcTemplate jdbcTemplate;

    @Value("${data-migration.data-dir}")
    private String dataDir;

    public record MatchResult(String table, long total, long exactMatch, long fuzzyMatch, long noMatch) {
    }

    @Transactional
    public MatchResult exportMatchingCsv(AddressTable table) {
        String t = table.tableName;
        long total = count("SELECT count(*) FROM " + t);

        jdbcTemplate.execute("DROP TABLE IF EXISTS address_exact_match");
        jdbcTemplate.execute("""
                CREATE TEMP TABLE address_exact_match AS
                SELECT DISTINCT ON (p.id) p.id AS row_id, b.bdrg_sn AS building_id
                FROM %s p
                JOIN building b ON b.is_deleted = false
                    AND b.sgg_cd_nm = p.sgg_nm AND b.stdg_cd_nm = p.bjdong_nm
                    AND b.mn_lotno = p.mn_lotno AND b.sub_lotno = p.sub_lotno
                ORDER BY p.id, b.bdrg_sn
                """.formatted(t));

        Path outputPath = Path.of(dataDir, "converted", t + "_building_mapping_export.csv");
        long[] counters = new long[2]; // [0]=exact, [1]=fuzzy
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("row_id,building_id,match_type");
            writer.newLine();

            jdbcTemplate.query("SELECT row_id, building_id FROM address_exact_match", rs -> {
                writeRow(writer, rs.getLong("row_id"), rs.getString("building_id"), "EXACT");
                counters[0]++;
            });

            // 지번(본번/부번)은 정확일치 그대로 요구하고, 법정동명 철자 편차만 pg_trgm으로 흡수한다 —
            // 같은 구 안에서 지번까지 정확히 같은데 동 이름만 다르면 오탈자일 가능성이 매우 높다.
            String fuzzySql = """
                    SELECT p.id AS row_id, best.bdrg_sn AS building_id
                    FROM %s p
                    CROSS JOIN LATERAL (
                        SELECT b.bdrg_sn
                        FROM building b
                        WHERE b.is_deleted = false
                          AND b.sgg_cd_nm = p.sgg_nm
                          AND b.mn_lotno = p.mn_lotno AND b.sub_lotno = p.sub_lotno
                          AND similarity(b.stdg_cd_nm, p.bjdong_nm) > ?
                        ORDER BY similarity(b.stdg_cd_nm, p.bjdong_nm) DESC
                        LIMIT 1
                    ) best
                    WHERE NOT EXISTS (SELECT 1 FROM address_exact_match m WHERE m.row_id = p.id)
                    """.formatted(t);
            jdbcTemplate.query(fuzzySql, rs -> {
                writeRow(writer, rs.getLong("row_id"), rs.getString("building_id"), "FUZZY");
                counters[1]++;
            }, SIMILARITY_THRESHOLD);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            jdbcTemplate.execute("DROP TABLE IF EXISTS address_exact_match");
        }

        MatchResult result = new MatchResult(t, total, counters[0], counters[1], total - counters[0] - counters[1]);
        logger.info("{}-building 매칭 CSV 출력 완료: {} (전체 {}건, EXACT {}, FUZZY {}, NO_MATCH {})",
                t, outputPath, result.total(), result.exactMatch(), result.fuzzyMatch(), result.noMatch());
        return result;
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private void writeRow(BufferedWriter writer, long rowId, String buildingId, String matchType) {
        try {
            writer.write(rowId + "," + buildingId + "," + matchType);
            writer.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
