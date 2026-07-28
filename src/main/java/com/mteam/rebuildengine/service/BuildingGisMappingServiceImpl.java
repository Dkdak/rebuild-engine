package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.model.entity.LegalDongCodeEntity;
import com.mteam.rebuildengine.repository.BuildingRepository;
import com.mteam.rebuildengine.repository.GisBuildingRepository;
import com.mteam.rebuildengine.repository.GisMatchCandidate;
import com.mteam.rebuildengine.repository.LegalDongCodeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

// building(F-13 원본) <-> gis_building(F-14 원본) 매칭 배치. 결과는 building_gis_mapping에 절대
// 직접 쓰지 않고 CSV로만 내보낸다(원본/가공 분리 원칙 연장, 2026-07-27) — 실제 반영은
// postgres/sql/load_building_gis_mapping_csv.sql이 F-12 §3.4 표준 성능 패턴으로 담당한다.
// 이 방식이 JPA saveAll보다 훨씬 빠르고, 로컬에서 만든 CSV 그대로 서버 DB에도 적재 가능해서
// F-14 §3.5(로컬→서버 매핑 결과 이전)가 별도 구현 없이 자동으로 해결된다.
//
// 성능 메모(2026-07-26): legal_dong_code(작음)와 gis_building(695K건, 매칭용 필드만 경량 프로젝션)을
// 전부 메모리에 올려두고 순수 인메모리로 매칭한다. DB 왕복은 building 페이징 조회만 남는다.
@Service
@RequiredArgsConstructor
public class BuildingGisMappingServiceImpl implements BuildingGisMappingService {

    private static final Logger logger = LogManager.getLogger(BuildingGisMappingServiceImpl.class);
    private static final int PAGE_SIZE = 1000;

    private static final String MATCH_EXACT = "EXACT";
    private static final String MATCH_ADDRESS = "ADDRESS_MATCH";
    private static final String MATCH_SCORE = "SCORE_BASED";
    private static final String MATCH_NONE = "NO_MATCH";
    private static final String MATCH_NO_DONG_CODE = "NO_DONG_CODE";

    private final BuildingRepository buildingRepository;
    private final LegalDongCodeRepository legalDongCodeRepository;
    private final GisBuildingRepository gisBuildingRepository;
    private final EntityManager entityManager;

    @Value("${data-migration.data-dir}")
    private String dataDir;

    @Override
    public MatchResult exportMatchingCsv() {
        Map<String, String> dongCodeByName = loadDongCodeLookup();
        logger.info("legal_dong_code 사전 로딩 완료: {}건", dongCodeByName.size());

        Map<String, List<GisMatchCandidate>> gisByPnu = new HashMap<>();
        Map<String, List<GisMatchCandidate>> gisByAddress = new HashMap<>();
        gisBuildingRepository.findAllForMatching().forEach(c -> {
            gisByPnu.computeIfAbsent(c.pnu(), k -> new ArrayList<>()).add(c);
            gisByAddress.computeIfAbsent(addressKey(c.bjdongCd(), c.mnLotno(), c.subLotno()), k -> new ArrayList<>()).add(c);
        });
        logger.info("gis_building 사전 로딩 완료: {}건", gisByPnu.values().stream().mapToInt(List::size).sum());

        MatchCounter counter = new MatchCounter();
        long startTime = System.currentTimeMillis();
        Path outputPath = Path.of(dataDir, "converted", "building_gis_mapping_export.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("bdrg_sn,pnu,building_ufid,part_no,match_type,candidate_count");
            writer.newLine();

            processPages(building -> {
                MatchOutcome outcome = matchOne(building, dongCodeByName, gisByPnu, gisByAddress);
                writeRow(writer, building.getBdrgSn(), outcome);
                counter.accept(outcome);
                if (counter.total % 50000 == 0) {
                    logger.info("건물-GIS 매칭 진행: {}건 ({}ms)", counter.total, System.currentTimeMillis() - startTime);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        MatchResult result = counter.toResult();
        logger.info("건물-GIS 매칭 CSV 출력 완료: {} ({}건, EXACT {}, ADDRESS {}, SCORE {}, NO_MATCH {}, NO_DONG_CODE {})",
                outputPath, result.total(), result.exact(), result.addressMatch(), result.scoreBased(),
                result.noMatch(), result.noDongCode());
        return result;
    }

    // building을 keyset pagination(bdrg_sn 기준)으로 순회하며 consumer를 호출한다 — 페이지 조회
    // 책임만 담당(매칭/집계는 호출부 몫). OFFSET 페이징(PageRequest)은 뒤 페이지로 갈수록 그 앞의
    // 모든 행을 훑어야 해서 585K건 배치에서 점점 느려지는 문제가 실측됐다(2026-07-27) — bdrg_sn(PK)
    // 인덱스로 다음 구간을 바로 찾는 keyset 방식은 페이지 위치와 무관하게 속도가 일정하다.
    // lastBdrgSn이 null이 되는 시점(마지막 페이지, 결과가 PAGE_SIZE보다 적음)이 종료조건.
    private void processPages(Consumer<BuildingEntity> consumer) {
        for (String lastBdrgSn = ""; lastBdrgSn != null; ) {
            List<BuildingEntity> batch =
                    buildingRepository.findByBdrgSnGreaterThanOrderByBdrgSnAsc(lastBdrgSn, Pageable.ofSize(PAGE_SIZE));
            batch.forEach(consumer);

            // 페이지마다 영속성 컨텍스트를 비우지 않으면 세션에 엔티티가 계속 누적되어
            // 페이지가 진행될수록 점점 느려진다(2026-07-26 실측, 2026-07-27 리팩터링 중 재발 확인).
            entityManager.clear();

            lastBdrgSn = batch.size() == PAGE_SIZE ? batch.get(batch.size() - 1).getBdrgSn() : null;
        }
    }

    // 매칭 결과 집계 책임만 담당하는 상태 객체 — MatchResult가 record라 자체 접근자를 제공하므로
    // 별도 getter는 두지 않는다(같은 최상위 클래스 안이라 outer에서 필드 직접 접근 가능).
    private static final class MatchCounter {
        private int total;
        private int exact;
        private int addressMatch;
        private int scoreBased;
        private int noMatch;
        private int noDongCode;

        void accept(MatchOutcome outcome) {
            total++;
            switch (outcome.matchType) {
                case MATCH_EXACT -> exact++;
                case MATCH_ADDRESS -> addressMatch++;
                case MATCH_SCORE -> scoreBased++;
                case MATCH_NO_DONG_CODE -> noDongCode++;
                default -> noMatch++;
            }
        }

        MatchResult toResult() {
            return new MatchResult(total, exact, addressMatch, scoreBased, noMatch, noDongCode);
        }
    }

    private Map<String, String> loadDongCodeLookup() {
        return legalDongCodeRepository.findAll().stream()
                .collect(Collectors.toMap(
                        e -> dongKey(e.getSggNm(), e.getBjdongNm()),
                        LegalDongCodeEntity::getBjdongCd,
                        (existing, replacement) -> replacement));
    }

    private String dongKey(String sggNm, String bjdongNm) {
        return sggNm + "|" + bjdongNm;
    }

    private String addressKey(String bjdongCd, String mnLotno, String subLotno) {
        return bjdongCd + "|" + mnLotno + "|" + subLotno;
    }

    private record MatchOutcome(GisMatchCandidate selected, String pnu, int candidateCount, String matchType) {
    }

    private MatchOutcome matchOne(BuildingEntity building, Map<String, String> dongCodeByName,
                                   Map<String, List<GisMatchCandidate>> gisByPnu,
                                   Map<String, List<GisMatchCandidate>> gisByAddress) {
        String bjdongCd = dongCodeByName.get(dongKey(building.getSggCdNm(), building.getStdgCdNm()));
        if (bjdongCd == null) {
            return new MatchOutcome(null, null, 0, MATCH_NO_DONG_CODE);
        }

        String plotGb = mapPlotGb(building.getPlotSeCdNm());
        String mnLotno = building.getMnLotno();
        String subLotno = building.getSubLotno();

        String computedPnu = null;
        List<GisMatchCandidate> candidates = List.of();
        if (plotGb != null && mnLotno != null && subLotno != null) {
            computedPnu = bjdongCd + plotGb + mnLotno + subLotno;
            candidates = gisByPnu.getOrDefault(computedPnu, List.of());
        }

        if (!candidates.isEmpty()) {
            if (candidates.size() == 1) {
                return new MatchOutcome(candidates.get(0), computedPnu, 1, MATCH_EXACT);
            }
            GisMatchCandidate best = pickBestByScore(building, candidates);
            return new MatchOutcome(best, computedPnu, candidates.size(), MATCH_SCORE);
        }

        // PNU 정확 매칭 실패 -> 대지구분 무시하고 법정동코드+본번+부번만으로 재조회
        List<GisMatchCandidate> addressCandidates =
                gisByAddress.getOrDefault(addressKey(bjdongCd, mnLotno, subLotno), List.of());
        if (addressCandidates.isEmpty()) {
            return new MatchOutcome(null, computedPnu, 0, MATCH_NONE);
        }
        if (addressCandidates.size() == 1) {
            return new MatchOutcome(addressCandidates.get(0), computedPnu, 1, MATCH_ADDRESS);
        }
        GisMatchCandidate best = pickBestByScore(building, addressCandidates);
        return new MatchOutcome(best, computedPnu, addressCandidates.size(), MATCH_SCORE);
    }

    // "대지"/"산" 텍스트를 PNU의 대지구분 숫자(1/2)로 변환. 그 외 값이면 PNU 계산 불가로 null.
    private String mapPlotGb(String plotSeCdNm) {
        if (plotSeCdNm == null) {
            return null;
        }
        if (plotSeCdNm.contains("산")) {
            return "2";
        }
        if (plotSeCdNm.contains("대지")) {
            return "1";
        }
        return null;
    }

    // 연면적 차이(최우선) -> 건축면적 차이(차순위) -> 주용도 일치(가점) -> UFID 오름차순(최종 동점 처리) 순으로 점수화
    private GisMatchCandidate pickBestByScore(BuildingEntity building, List<GisMatchCandidate> candidates) {
        GisMatchCandidate best = null;
        BigDecimal bestScore = null;
        for (GisMatchCandidate candidate : candidates) {
            BigDecimal score = computeScore(building, candidate);
            boolean better = bestScore == null || score.compareTo(bestScore) > 0;
            boolean tie = bestScore != null && score.compareTo(bestScore) == 0 && best != null
                    && compareUfid(candidate, best) < 0;
            if (better || tie) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private int compareUfid(GisMatchCandidate a, GisMatchCandidate b) {
        String ua = a.buildingUfid() == null ? "" : a.buildingUfid();
        String ub = b.buildingUfid() == null ? "" : b.buildingUfid();
        return ua.compareTo(ub);
    }

    private BigDecimal computeScore(BuildingEntity building, GisMatchCandidate candidate) {
        BigDecimal score = BigDecimal.ZERO;
        if (building.getGfa() != null && candidate.totalFloorArea() != null) {
            BigDecimal diff = building.getGfa().subtract(candidate.totalFloorArea()).abs();
            score = score.subtract(diff.multiply(BigDecimal.valueOf(1_000_000)));
        }
        if (building.getBdar() != null && candidate.archArea() != null) {
            BigDecimal diff = building.getBdar().subtract(candidate.archArea()).abs();
            score = score.subtract(diff.multiply(BigDecimal.valueOf(1_000)));
        }
        if (building.getMnUsgCdNm() != null && building.getMnUsgCdNm().equals(candidate.mainPurposeNm())) {
            score = score.add(BigDecimal.ONE);
        }
        return score;
    }

    // Consumer<BuildingEntity> 람다 안에서 호출되므로 checked IOException을 밖으로 던질 수 없다 —
    // UncheckedIOException으로 감싸서 exportMatchingCsv()의 try-with-resources까지 그대로 전파한다.
    private void writeRow(BufferedWriter writer, String bdrgSn, MatchOutcome outcome) {
        try {
            GisMatchCandidate selected = outcome.selected();
            String buildingUfid = selected == null ? "" : csv(selected.buildingUfid());
            String partNo = selected == null ? "" : String.valueOf(selected.partNo());
            writer.write(String.join(",",
                    csv(bdrgSn), csv(outcome.pnu()), buildingUfid, partNo,
                    csv(outcome.matchType()), String.valueOf(outcome.candidateCount())));
            writer.newLine();
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
