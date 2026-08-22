package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.LegalDongCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LegalDongCodeRepository extends JpaRepository<LegalDongCodeEntity, String> {
    Optional<LegalDongCodeEntity> findBySggNmAndBjdongNm(String sggNm, String bjdongNm);

    // 통합 검색 GU 후보(search_index.type='GU') 선택 시 sigunguCd -> sggNm 변환용 — 같은 구의 법정동
    // 행은 전부 같은 sggNm이라 아무 한 건이나 찾으면 된다.
    Optional<LegalDongCodeEntity> findFirstBySigunguCd(String sigunguCd);

    // F-03 대시보드 자치구 순위 — building.sgg_cd_nm(이름) -> sigunguCd(코드) 역변환, 위와 동일한
    // "같은 구는 전부 같은 코드" 이유로 아무 한 건이나 찾으면 된다. 25개뿐이라 배치 끝에 구별로 1회씩만 호출.
    Optional<LegalDongCodeEntity> findFirstBySggNm(String sggNm);
}
