package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BuildingRepository extends JpaRepository<BuildingEntity, String>,
        JpaSpecificationExecutor<BuildingEntity>, BuildingRepositoryCustom {
    // keyset pagination — OFFSET 기반 페이징은 뒤 페이지로 갈수록 앞의 모든 행을 훑어야 해서
    // 585K건 배치에서 페이지가 진행될수록 점점 느려지는 문제가 있었다(2026-07-27 실측 재현).
    // bdrg_sn(PK) 인덱스로 직접 다음 구간을 찾기 때문에 페이지 위치와 무관하게 속도가 일정하다.
    List<BuildingEntity> findByBdrgSnGreaterThanOrderByBdrgSnAsc(String bdrgSn, Pageable pageable);

    // 법정동 단위 조회(F-05 §3.1) — 결과가 한 동 범위로 좁혀져 585K건 배치와 달리 OFFSET 페이징으로 충분하다.
    @Query("""
            SELECT b FROM BuildingEntity b
            WHERE b.sggCdNm = :sggNm AND b.stdgCdNm = :bjdongNm
              AND b.isDeleted = false
              AND (:platGbCd IS NULL OR b.plotSeCdNm = :platGbCd)
              AND (:bun IS NULL OR b.mnLotno = :bun)
              AND (:ji IS NULL OR b.subLotno = :ji)
            """)
    Page<BuildingEntity> searchByDong(String sggNm, String bjdongNm, String platGbCd, String bun, String ji, Pageable pageable);

    // F-04 §3.1 properties/search 전용 검색(위치+area/buildYear/propertyTypes)은 조건이 많고 동적으로
    // 바뀌어 HELP6 §1·§4·§5에 따라 Query Method/긴 @Query 대신 Custom Repository(Criteria API)로 뺐다
    // — BuildingRepositoryCustom.search(BuildingSearchCriteria, Pageable), 구현은 BuildingRepositoryImpl.
}
