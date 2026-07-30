package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.LegalDongCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LegalDongCodeRepository extends JpaRepository<LegalDongCodeEntity, String> {
    Optional<LegalDongCodeEntity> findBySggNmAndBjdongNm(String sggNm, String bjdongNm);

    // 통합 검색 GU 후보(search_index.type='GU') 선택 시 sigunguCd -> sggNm 변환용 — 같은 구의 법정동
    // 행은 전부 같은 sggNm이라 아무 한 건이나 찾으면 된다.
    Optional<LegalDongCodeEntity> findFirstBySigunguCd(String sigunguCd);
}
