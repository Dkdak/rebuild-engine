package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.GisBuildingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GisBuildingRepository extends JpaRepository<GisBuildingEntity, Long> {
    Optional<GisBuildingEntity> findByPnuAndBuildingUfidAndPartNo(String pnu, String buildingUfid, int partNo);

    List<GisBuildingEntity> findByPnu(String pnu);

    List<GisBuildingEntity> findByBjdongCdAndMnLotnoAndSubLotno(String bjdongCd, String mnLotno, String subLotno);

    // 매핑 배치가 건별로 DB를 왕복하지 않고 전체를 한 번에 메모리로 올려 쓰기 위한 경량 프로젝션 조회
    @Query("SELECT new com.mteam.rebuildengine.repository.GisMatchCandidate(" +
            "g.id, g.pnu, g.bjdongCd, g.mnLotno, g.subLotno, g.totalFloorArea, g.archArea, g.mainPurposeNm, g.buildingUfid) " +
            "FROM GisBuildingEntity g")
    List<GisMatchCandidate> findAllForMatching();
}
