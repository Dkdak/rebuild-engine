package com.mteam.rebuildengine.service.analysis;

import com.mteam.rebuildengine.mapper.AgingRequirementMapper;
import com.mteam.rebuildengine.model.entity.AgingFactorEntity;
import com.mteam.rebuildengine.model.entity.CostBasePriceEntity;
import com.mteam.rebuildengine.model.entity.PermitValidityPeriodEntity;
import com.mteam.rebuildengine.model.entity.StructureIndexEntity;
import com.mteam.rebuildengine.model.entity.UsageIndexEntity;
import com.mteam.rebuildengine.model.entity.ZoningLimitEntity;
import com.mteam.rebuildengine.model.read.AgingRequirementReadModel;
import com.mteam.rebuildengine.repository.AgingFactorRepository;
import com.mteam.rebuildengine.repository.CostBasePriceRepository;
import com.mteam.rebuildengine.repository.PermitValidityPeriodRepository;
import com.mteam.rebuildengine.repository.StructureIndexRepository;
import com.mteam.rebuildengine.repository.UsageIndexRepository;
import com.mteam.rebuildengine.repository.ZoningLimitRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// F-06/F-07이 조회하는 작은 법령 참조 테이블(전부 20행 이하, 운영 중 사실상 불변)을 요청마다 DB에서
// 다시 읽지 않고 앱 기동 시 1회만 로드해 재사용한다 — F-09 V1 배치가 건물 585,331건을 순회하며 이
// 테이블들을 매번 조회하던 것이 병목의 상당 부분이었다(2026-08-08 실측, 건당 15~20개 쿼리 중 7개).
// 라이브 단건 조회(F-05/F-06/F-07)에도 그대로 이득 — 참조 테이블은 재배포 전까진 안 바뀐다.
@Component
@RequiredArgsConstructor
public class ReferenceDataCache {

    private final ZoningLimitRepository zoningLimitRepository;
    private final StructureIndexRepository structureIndexRepository;
    private final UsageIndexRepository usageIndexRepository;
    private final AgingFactorRepository agingFactorRepository;
    private final CostBasePriceRepository costBasePriceRepository;
    private final PermitValidityPeriodRepository permitValidityPeriodRepository;
    private final AgingRequirementMapper agingRequirementMapper;

    private Map<String, ZoningLimitEntity> zoningLimits;
    private Map<String, StructureIndexEntity> structureIndexes;
    private Map<String, UsageIndexEntity> usageIndexes;
    private Map<String, AgingFactorEntity> agingFactors;
    private List<CostBasePriceEntity> costBasePricesDesc;
    private int permitValidityYears;

    // aging_requirement의 curve 조회(findCurve)는 승인연도별로 키가 갈려 "전부 로드" 대신 실제로
    // 조회된 조합만 메모이즈(bounded — 1981~1991년 × floorTier 2종 = 최대 22개 키).
    private final Map<String, Optional<AgingRequirementReadModel>> agingRequirementFlatCache = new ConcurrentHashMap<>();
    private final Map<String, Optional<AgingRequirementReadModel>> agingRequirementCurveCache = new ConcurrentHashMap<>();

    @PostConstruct
    void load() {
        zoningLimits = zoningLimitRepository.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(ZoningLimitEntity::getZoneName, e -> e));
        structureIndexes = structureIndexRepository.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(StructureIndexEntity::getCode, e -> e));
        usageIndexes = usageIndexRepository.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(UsageIndexEntity::getCode, e -> e));
        agingFactors = agingFactorRepository.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(AgingFactorEntity::getStructure, e -> e));
        costBasePricesDesc = costBasePriceRepository.findAll().stream()
                .sorted(Comparator.comparing(CostBasePriceEntity::getEffectiveDate).reversed())
                .toList();
        permitValidityYears = permitValidityPeriodRepository.findAll().stream().findFirst()
                .map(PermitValidityPeriodEntity::getValidityYears)
                .orElseThrow(() -> new IllegalStateException("permit_validity_period 시드 누락"));
    }

    public Optional<ZoningLimitEntity> zoningLimit(String zoneName) {
        return Optional.ofNullable(zoningLimits.get(zoneName));
    }

    // FEATURE_19 §2.2-e(2026-08-27 추가) — STEP1 용도지역 드롭다운용, 16개 전체를 그대로 내려준다.
    public List<ZoningLimitEntity> allZoningLimits() {
        return List.copyOf(zoningLimits.values());
    }

    public StructureIndexEntity structureIndex(String code) {
        StructureIndexEntity entity = structureIndexes.get(code);
        if (entity == null) {
            throw new IllegalStateException("structure_index 데이터 누락: " + code);
        }
        return entity;
    }

    public Optional<UsageIndexEntity> usageIndex(String code) {
        return Optional.ofNullable(usageIndexes.get(code));
    }

    public AgingFactorEntity agingFactor(String structureGroup) {
        AgingFactorEntity entity = agingFactors.get(structureGroup);
        if (entity == null) {
            throw new IllegalStateException("aging_factor 데이터 누락: " + structureGroup);
        }
        return entity;
    }

    // FEATURE_07_COST.md §3.2 — 기준일 이하 중 가장 최근 effective_date 행.
    public CostBasePriceEntity costBasePrice(LocalDate asOf) {
        return costBasePricesDesc.stream()
                .filter(row -> !row.getEffectiveDate().isAfter(asOf))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("cost_base_price 시드 누락"));
    }

    public int permitValidityYears() {
        return permitValidityYears;
    }

    public Optional<AgingRequirementReadModel> findFlat(String housingType, String structureGroup) {
        return agingRequirementFlatCache.computeIfAbsent(housingType + "|" + structureGroup,
                key -> agingRequirementMapper.findFlat(housingType, structureGroup));
    }

    public Optional<AgingRequirementReadModel> findCurve(String housingType, String structureGroup, String floorTier,
                                                          int approvalYear) {
        String key = housingType + "|" + structureGroup + "|" + floorTier + "|" + approvalYear;
        return agingRequirementCurveCache.computeIfAbsent(key,
                k -> agingRequirementMapper.findCurve(housingType, structureGroup, floorTier, approvalYear));
    }
}
