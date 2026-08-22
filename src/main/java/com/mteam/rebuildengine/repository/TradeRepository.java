package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.TradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<TradeEntity, Long> {
    // F-04 §2.1-h "최근 실거래가" — 해제(취소)된 거래는 제외. 건물당 여러 건일 수 있어 최신순으로
    // 받아 Service가 building_id별 첫 번째 값(가장 최근)만 취한다.
    List<TradeEntity> findByBuildingIdInAndCancelDateIsNullOrderByContractDateDesc(List<String> buildingIds);

    // F-03 대시보드 dataStatus — F-09 배치 마지막에 1회만 호출(2026-08-23). count()는 JpaRepository 상속.
    long countByBuildingIdIsNotNull();
    Optional<TradeEntity> findTopByOrderByContractDateDesc();
}
