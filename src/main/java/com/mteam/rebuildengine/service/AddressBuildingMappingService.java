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

    // apartment_price는 여기 없다 — "동(건물)이 여러 개인 단지" 문제(FEATURE_16 §5.1) 때문에
    // exportApartmentPriceMatchingCsv()에 building_dong_nm 인식 로직을 따로 둔다(2026-08-08).
    public enum AddressTable {
        LAND_PRICE("land_price"), LANDUSE("landuse"), PERMIT("permit"),
        BUILDING_SUMMARY("building_summary"), LANDUSE_DISTRICT("landuse_district"),
        DETACHED_HOUSE_PRICE("detached_house_price");

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

    // FEATURE_16_PRICE_DATA_MIGRATION.md §5.1(2026-08-08 발견) — 동(건물)이 여러 개인 아파트 단지에서
    // 시군구+법정동+본번+부번만으로는 건물을 특정 못 해, 그 지번의 apartment_price 행 전부가 bdrg_sn
    // 정렬상 가장 작은 값(하필 지하주차장 등 비주거 건물일 수 있음) 하나로만 몰리던 문제. 실제 사례:
    // 노원구 상계동 1309(한일유앤아이) — 101~105동(세대수 有) 전부 매칭 0건, 지하주차장(세대수 0)에만
    // apartment_price 5개 동 데이터가 전부 몰림.
    //
    // 해법: apartment_price.building_dong_nm("101")이 있으면 building.dng_nm("101동")과 일치하는
    // 건물로만 좁힌다(끝의 "동" 접미사는 여기서 붙여서 비교) — 일치하는 게 없으면 그 행은 매칭 실패로
    // 남긴다(엉뚱한 동에 잘못 매칭하는 것보다 안전, `DOMAIN.md` §4). building_dong_nm이 비어있으면(동
    // 구분 없는 단지) 기존처럼 지번만으로 매칭하되, 후보가 여럿이면 세대수(hh_cnt)가 있는 건물을
    // 우선한다 — 지하주차장/관리동/경비실(세대수 0)보다 실제 거주 동을 고를 확률을 높인다.
    //
    // ✅ 보강(2026-08-08, 실측 재조사): 위 규칙만으로는 "그 지번에 건물이 딱 하나뿐인데 dng_nm이
    // 비어있거나 표기가 다른"(예: 신대방동 360-171 "한길아트빌" — building_dong_nm='1'인데 building.
    // dng_nm=''이라 "1동"과 안 맞아 매칭 실패) 경우를 놓친다. 그 지번의 건물 후보가 1개뿐이면(candidate_
    // count=1) 동 이름이 안 맞아도 무조건 매칭한다 — 후보가 하나뿐이면 애초에 헷갈릴 여지가 없다.
    // 후보가 여럿일 때만 동 이름 일치를 엄격히 요구한다(한일유앤아이류 오매칭 방지, 위 문단 그대로).
    //
    // ✅ 보강 2(2026-08-08, 22,925건 잔여분 재조사) — building_dong_nm 표기가 원본마다 다르다: "101"
    // (숫자만, 168,693건 외 대다수)과 "101동"(이미 "동" 접미사 포함, 168,693건) 두 형식이 섞여 있는데,
    // 기존 로직은 항상 "동"을 붙여서 비교(`p.building_dong_nm || '동'`)해 "101동" 형식 원본은
    // "101동동"이 돼버려 전부 매칭 실패였다(실측: 동대문구 장안동 397-2 "엘림하우스" 101~104동).
    // 양쪽 다 끝의 "동"을 떼고 비교(regexp_replace)해 표기 차이를 흡수한다.
    @Transactional
    public MatchResult exportApartmentPriceMatchingCsv() {
        String t = "apartment_price";
        long total = count("SELECT count(*) FROM " + t);

        jdbcTemplate.execute("DROP TABLE IF EXISTS address_exact_match");
        jdbcTemplate.execute("""
                CREATE TEMP TABLE address_exact_match AS
                SELECT DISTINCT ON (p.id) p.id AS row_id, m.bdrg_sn AS building_id
                FROM apartment_price p
                JOIN LATERAL (
                    SELECT b.bdrg_sn, b.dng_nm, b.hh_cnt,
                           count(*) OVER () AS candidate_count
                    FROM building b
                    WHERE b.is_deleted = false
                      AND b.sgg_cd_nm = p.sgg_nm AND b.stdg_cd_nm = p.bjdong_nm
                      AND b.mn_lotno = p.mn_lotno AND b.sub_lotno = p.sub_lotno
                ) m ON (
                    m.candidate_count = 1
                    OR NULLIF(p.building_dong_nm, '') IS NULL
                    OR regexp_replace(m.dng_nm, '동$', '') = regexp_replace(p.building_dong_nm, '동$', '')
                )
                ORDER BY p.id,
                    CASE WHEN m.hh_cnt IS NOT NULL AND m.hh_cnt > 0 THEN 0 ELSE 1 END,
                    m.bdrg_sn
                """);

        Path outputPath = Path.of(dataDir, "converted", t + "_building_mapping_export.csv");
        long[] counters = new long[2]; // [0]=exact, [1]=fuzzy
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("row_id,building_id,match_type");
            writer.newLine();

            jdbcTemplate.query("SELECT row_id, building_id FROM address_exact_match", rs -> {
                writeRow(writer, rs.getLong("row_id"), rs.getString("building_id"), "EXACT");
                counters[0]++;
            });

            // 법정동명 철자 편차만 pg_trgm으로 흡수(기존 5개 테이블과 동일 폭)하되, building_dong_nm
            // 조건은 exact와 똑같이 유지(후보 1개면 무조건, 여럿이면 동 이름 일치 필수) — 동 이름까지
            // 애매하게 흡수하면 엉뚱한 동에 매칭될 위험이 커진다.
            String fuzzySql = """
                    SELECT p.id AS row_id, best.bdrg_sn AS building_id
                    FROM apartment_price p
                    CROSS JOIN LATERAL (
                        SELECT b.bdrg_sn
                        FROM (
                            SELECT b.bdrg_sn, b.dng_nm, b.hh_cnt, b.stdg_cd_nm,
                                   count(*) OVER () AS candidate_count
                            FROM building b
                            WHERE b.is_deleted = false
                              AND b.sgg_cd_nm = p.sgg_nm
                              AND b.mn_lotno = p.mn_lotno AND b.sub_lotno = p.sub_lotno
                              AND similarity(b.stdg_cd_nm, p.bjdong_nm) > ?
                        ) b
                        WHERE b.candidate_count = 1
                           OR NULLIF(p.building_dong_nm, '') IS NULL
                           OR regexp_replace(b.dng_nm, '동$', '') = regexp_replace(p.building_dong_nm, '동$', '')
                        ORDER BY similarity(b.stdg_cd_nm, p.bjdong_nm) DESC,
                            CASE WHEN b.hh_cnt IS NOT NULL AND b.hh_cnt > 0 THEN 0 ELSE 1 END
                        LIMIT 1
                    ) best
                    WHERE NOT EXISTS (SELECT 1 FROM address_exact_match m WHERE m.row_id = p.id)
                    """;
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
