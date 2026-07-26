package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.model.entity.BuildingGisMappingEntity;
import com.mteam.rebuildengine.model.entity.LegalDongCodeEntity;
import com.mteam.rebuildengine.repository.BuildingGisMappingRepository;
import com.mteam.rebuildengine.repository.BuildingRepository;
import com.mteam.rebuildengine.repository.GisBuildingRepository;
import com.mteam.rebuildengine.repository.GisMatchCandidate;
import com.mteam.rebuildengine.repository.LegalDongCodeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// building(F-13 원본) <-> gis_building(F-14 원본) 매칭 배치. 결과는 building_gis_mapping에만 저장하고,
// PNU 등 계산값은 building/gis_building 원본 테이블에 반영하지 않는다 (원본/가공 분리 원칙, 2026-07-26 확정).
//
// 성능 메모(2026-07-26): 최초 구현은 legal_dong_code/gis_building을 건물 row마다 개별 SELECT했다가
// 585K건 기준 3~4시간까지 나올 걸로 추정돼서, legal_dong_code(작음)와 gis_building(695K건, 매칭용 필드만
// 경량 프로젝션)을 전부 메모리에 올려두고 순수 인메모리로 매칭하도록 바꿨다. DB 왕복은 building 페이징 조회와
// building_gis_mapping 저장(SEQUENCE 배치 INSERT)만 남는다.
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
    private final BuildingGisMappingRepository buildingGisMappingRepository;
    private final EntityManager entityManager;

    @Override
    public MatchResult runMatching() {
        Map<String, String> dongCodeByName = loadDongCodeLookup();
        logger.info("legal_dong_code 사전 로딩 완료: {}건", dongCodeByName.size());

        Map<String, List<GisMatchCandidate>> gisByPnu = new HashMap<>();
        Map<String, List<GisMatchCandidate>> gisByAddress = new HashMap<>();
        for (GisMatchCandidate c : gisBuildingRepository.findAllForMatching()) {
            gisByPnu.computeIfAbsent(c.pnu(), k -> new ArrayList<>()).add(c);
            gisByAddress.computeIfAbsent(addressKey(c.bjdongCd(), c.mnLotno(), c.subLotno()), k -> new ArrayList<>()).add(c);
        }
        logger.info("gis_building 사전 로딩 완료: {}건", gisByPnu.values().stream().mapToInt(List::size).sum());

        int total = 0, exact = 0, addressMatch = 0, scoreBased = 0, noMatch = 0, noDongCode = 0;

        int page = 0;
        List<BuildingEntity> buildings;
        do {
            buildings = buildingRepository.findAll(PageRequest.of(page, PAGE_SIZE, Sort.by("bdrgSn"))).getContent();
            if (buildings.isEmpty()) {
                break;
            }

            List<String> buildingIds = buildings.stream().map(BuildingEntity::getBdrgSn).toList();
            Map<String, BuildingGisMappingEntity> existingByBuildingId = new HashMap<>();
            for (BuildingGisMappingEntity existing : buildingGisMappingRepository.findByBuildingIdIn(buildingIds)) {
                existingByBuildingId.put(existing.getBuildingId(), existing);
            }

            List<BuildingGisMappingEntity> toSave = new ArrayList<>();
            for (BuildingEntity building : buildings) {
                total++;
                MatchOutcome outcome = matchOne(building, dongCodeByName, gisByPnu, gisByAddress);
                toSave.add(toMappingEntity(building.getBdrgSn(), outcome, existingByBuildingId.get(building.getBdrgSn())));
                switch (outcome.matchType) {
                    case MATCH_EXACT -> exact++;
                    case MATCH_ADDRESS -> addressMatch++;
                    case MATCH_SCORE -> scoreBased++;
                    case MATCH_NO_DONG_CODE -> noDongCode++;
                    default -> noMatch++;
                }
            }
            buildingGisMappingRepository.saveAll(toSave);

            // 페이지마다 영속성 컨텍스트를 비우지 않으면 세션에 엔티티가 계속 누적되어
            // 페이지가 진행될수록 점점 느려진다(실측 확인: 5만건 54초 -> 25만건대 144초로 저하).
            entityManager.clear();

            page++;
            if (total % 50000 == 0) {
                logger.info("건물-GIS 매핑 진행: {}건", total);
            }
        } while (true);

        logger.info("건물-GIS 매핑 완료: 전체 {}건 (EXACT {}, ADDRESS {}, SCORE {}, NO_MATCH {}, NO_DONG_CODE {})",
                total, exact, addressMatch, scoreBased, noMatch, noDongCode);
        return new MatchResult(total, exact, addressMatch, scoreBased, noMatch, noDongCode);
    }

    private Map<String, String> loadDongCodeLookup() {
        Map<String, String> map = new HashMap<>();
        for (LegalDongCodeEntity entity : legalDongCodeRepository.findAll()) {
            map.put(dongKey(entity.getSggNm(), entity.getBjdongNm()), entity.getBjdongCd());
        }
        return map;
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

    private BuildingGisMappingEntity toMappingEntity(String buildingId, MatchOutcome outcome,
                                                       BuildingGisMappingEntity existing) {
        Long gisBuildingId = outcome.selected() == null ? null : outcome.selected().id();
        BuildingGisMappingEntity fresh = BuildingGisMappingEntity.builder()
                .buildingId(buildingId)
                .gisBuildingId(gisBuildingId)
                .pnu(outcome.pnu())
                .candidateCount(outcome.candidateCount())
                .matchType(outcome.matchType())
                .score(null)
                .build();

        if (existing == null) {
            return fresh;
        }
        existing.updateFrom(fresh);
        return existing;
    }
}
