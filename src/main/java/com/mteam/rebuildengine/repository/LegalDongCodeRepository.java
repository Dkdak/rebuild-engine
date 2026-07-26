package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.LegalDongCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LegalDongCodeRepository extends JpaRepository<LegalDongCodeEntity, String> {
    Optional<LegalDongCodeEntity> findBySggNmAndBjdongNm(String sggNm, String bjdongNm);
}
