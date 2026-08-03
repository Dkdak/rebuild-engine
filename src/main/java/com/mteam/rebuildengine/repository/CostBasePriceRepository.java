package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.CostBasePriceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface CostBasePriceRepository extends JpaRepository<CostBasePriceEntity, LocalDate> {

    // FEATURE_07_COST.md §3.2 — 기준일 이하 중 가장 최근 effective_date 행을 사용.
    Optional<CostBasePriceEntity> findFirstByEffectiveDateLessThanEqualOrderByEffectiveDateDesc(LocalDate asOf);
}
