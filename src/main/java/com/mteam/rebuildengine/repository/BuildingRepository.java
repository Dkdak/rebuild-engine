package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BuildingRepository extends JpaRepository<BuildingEntity, String> {
    // keyset pagination — OFFSET 기반 페이징은 뒤 페이지로 갈수록 앞의 모든 행을 훑어야 해서
    // 585K건 배치에서 페이지가 진행될수록 점점 느려지는 문제가 있었다(2026-07-27 실측 재현).
    // bdrg_sn(PK) 인덱스로 직접 다음 구간을 찾기 때문에 페이지 위치와 무관하게 속도가 일정하다.
    // isAncillary=false(2026-08-08)·isOutOfScope=false(2026-08-09)·isDeleted=false(2026-08-09, 삭제된
    // 건물이 investment_result 배치에 남는 버그 수정 — 이 메서드에 isDeleted 조건이 아예 없었다) — 셋 다
    // F-09(등급·ROI)·F-14(GIS 매핑) 배치가 계산할 이유가 없는 건물이라 이 공유 페이징 쿼리 단계에서부터 걸러낸다.
    List<BuildingEntity> findByBdrgSnGreaterThanAndIsAncillaryFalseAndIsOutOfScopeFalseAndIsDeletedFalseOrderByBdrgSnAsc(String bdrgSn, Pageable pageable);

    // FEATURE_04_SEARCH.md §5.1(2026-08-08)·§0-D(2026-08-09) — F-05~F-09 단건 조회(findById 대신) 전부
    // 이걸 쓴다. 매매 불가 부속시설·6종 밖 용도·삭제된 건물은 buildingId를 직접 알아도 상세조회가 안 되게
    // (존재 자체가 없는 것처럼) 막는다.
    Optional<BuildingEntity> findByBdrgSnAndIsAncillaryFalseAndIsOutOfScopeFalseAndIsDeletedFalse(String bdrgSn);

    // F-05 buildings/title(동 단위 조회), F-04 properties/search(조건이 많고 동적)는 HELP6 §3·§5에 따라
    // Query Method 대신 Mapper(BuildingMapper)로 뺐다. findById/save 등 단순 조회·쓰기만 여기 남는다.

    // F-03 대시보드 집계 "서울 건축물 전체"(is_ancillary/is_out_of_scope 제외 전) — 배치가 순회하는
    // findByBdrgSnGreaterThan...IsAncillaryFalseIsOutOfScopeFalse...는 이미 걸러진 뒤라 이 카운트는 별도.
    long countByIsDeletedFalse();
}
