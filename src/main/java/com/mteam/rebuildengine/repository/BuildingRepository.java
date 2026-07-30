package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BuildingRepository extends JpaRepository<BuildingEntity, String> {
    // keyset pagination — OFFSET 기반 페이징은 뒤 페이지로 갈수록 앞의 모든 행을 훑어야 해서
    // 585K건 배치에서 페이지가 진행될수록 점점 느려지는 문제가 있었다(2026-07-27 실측 재현).
    // bdrg_sn(PK) 인덱스로 직접 다음 구간을 찾기 때문에 페이지 위치와 무관하게 속도가 일정하다.
    List<BuildingEntity> findByBdrgSnGreaterThanOrderByBdrgSnAsc(String bdrgSn, Pageable pageable);

    // F-05 buildings/title(동 단위 조회), F-04 properties/search(조건이 많고 동적)는 HELP6 §3·§5에 따라
    // Query Method 대신 Mapper(BuildingMapper)로 뺐다. findById/save 등 단순 조회·쓰기만 여기 남는다.
}
