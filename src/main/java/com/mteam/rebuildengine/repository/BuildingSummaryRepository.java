package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.BuildingSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BuildingSummaryRepository extends JpaRepository<BuildingSummaryEntity, Long> {
    // F-05 §2.1 "단지 정보" — 단지 내 여러 동(building)이 같은 총괄표제부 행에 매칭될 수 있어(1:N
    // 가능성) 방어적으로 List로 받는다(ApartmentPriceRepository와 동일 이유).
    List<BuildingSummaryEntity> findByBuildingId(String buildingId);
}
