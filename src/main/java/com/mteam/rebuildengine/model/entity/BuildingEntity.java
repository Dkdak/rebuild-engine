package com.mteam.rebuildengine.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 서울열린데이터광장 OpenAPI(vBigDjrTitle) 응답을 그대로 적재하는 테이블.
// 배치가 서울 전체를 미리 수집해 채우며, 조회는 항상 이 테이블만 본다 (FEATURE_12_DATA_BATCH.md §B, §3.7).
@Entity
@Table(name = "building")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuildingEntity {

    @Id
    @Column(length = 50)
    private String bdrgSn;

    @Column(length = 500)
    private String platPlc;
    @Column(length = 100)
    private String sggCdNm;
    @Column(length = 100)
    private String stdgCdNm;
    @Column(length = 50)
    private String plotSeCdNm;
    @Column(length = 20)
    private String mnLotno;
    @Column(length = 20)
    private String subLotno;
    @Column(length = 100)
    private String spareaNm;
    @Column(length = 20)
    private String blckNo;
    @Column(length = 20)
    private String ltNo;
    @Column(length = 100)
    private String naRoadCdNm;
    @Column(length = 100)
    private String naStdgCdNm;
    @Column(length = 100)
    private String naGugseCdNm;
    @Column(length = 20)
    private String naMnLotno;
    @Column(length = 20)
    private String naSubLotno;
    @Column(length = 50)
    private String ldgrSeCdNm;
    @Column(length = 50)
    private String ldgrKindCdNm;
    @Column(length = 100)
    private String dngNm;
    @Column(length = 50)
    private String manxSeCdNm;

    private BigDecimal siar;
    private BigDecimal bdar;
    private BigDecimal bdcvrt;
    private BigDecimal gfa;
    private BigDecimal fartCmpttnGfa;
    private BigDecimal fart;

    @Column(length = 200)
    private String strctCdNm;
    @Column(length = 500)
    private String etcStrctInfo;
    @Column(length = 200)
    private String mnUsgCdNm;
    @Column(length = 500)
    private String etcUsgCn;
    @Column(length = 100)
    private String roofCdNm;
    @Column(length = 500)
    private String etcRoofNm;

    private Integer hhCnt;
    private Integer fmlCnt;
    private Integer hoCnt;
    private Integer grndNofl;
    private Integer udgdNofl;
    private BigDecimal hg;

    private Integer psngrElvtrCnt;
    private Integer euseElvtrCnt;
    private Integer anxBdstCnt;
    private BigDecimal anxBdstArea;
    private BigDecimal tolDngGfa;

    private Integer indrMcnclCntom;
    private BigDecimal indrMcnclArea;
    private Integer otdrMcnclCntom;
    private BigDecimal otdrMcnclArea;
    private Integer indrSfprplCntom;
    private BigDecimal indrSfprplArea;
    private Integer otdrSfprplCntom;
    private BigDecimal otdrSfprplArea;

    private LocalDate prmsnYmd;
    private LocalDate bgncstYmd;
    private LocalDate useAprvYmd;

    @Column(length = 50)
    private String enrgEfcyGrdVl;
    private BigDecimal enrgRtrdt;
    private BigDecimal epiScr;
    @Column(length = 50)
    private String ecfrdBdstGrdVl;
    private BigDecimal ecfrdBdstCertScr;
    @Column(length = 50)
    private String intgBdstGrdVl;
    private BigDecimal intgBdstCertScr;

    @Column(length = 5)
    private String rserDesignAplcnYn;
    @Column(length = 100)
    private String rserAbltCn;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private boolean isDeleted;

    // FEATURE_04_SEARCH.md §5.1(2026-08-08) — 경비실/주차장/관리동/상가 등 매매 불가능한 부속시설
    // 판정 결과. 원본 CSV엔 없는 파생 플래그라 @Builder 생성자엔 안 넣는다 — postgres/sql/
    // seed_building_ancillary_flag.sql(dng_nm 키워드 + hh_cnt=0/null 조건)이 직접 SQL로 채운다.
    // BuildingRepository.findByBdrgSnAndIsAncillaryFalse...()로 F-05~F-09 단건 조회를, BuildingMapper.xml의
    // excludeNonSellableBuildings로 F-04 검색을, findByBdrgSnGreaterThanAndIsAncillaryFalse...로
    // F-09 배치·F-14 GIS 매핑 배치를 각각 걸러낸다 — 판정 로직 자체는 이 컬럼 하나로만 관리.
    @Column(nullable = false)
    private boolean isAncillary;

    // FEATURE_04_SEARCH.md §0-D(2026-08-09, 기획 확정) — PropertyTypeClassifier.classify()가 끝내 6종
    // (아파트/연립다세대/단독다가구/오피스텔/상업업무용/공장창고, DOMAIN.md §1) 중 하나로 분류하지 못하는
    // 건물 판정 결과: ① 학교/교회/병원/군사시설 등 대응되는 mn_usg_cd_nm이 아예 없는 경우, ② '공동주택'인데
    // grnd_nofl(지상층수)이 없어 아파트/연립다세대 판정이 안 되는 경우. isAncillary와 원인은 다르지만
    // (부속시설 vs 분류 불가) 효과는 같아 — 매매/투자분석 대상이 될 수 없는 건물이라 같은 방식(SQL로
    // 1회 계산해 컬럼에 저장, postgres/sql/seed_building_scope_flag.sql)으로 검색·배치·단건조회 전부에서 뺀다.
    @Column(nullable = false)
    private boolean isOutOfScope;

    @Builder
    public BuildingEntity(String bdrgSn, String platPlc, String sggCdNm, String stdgCdNm, String plotSeCdNm,
                           String mnLotno, String subLotno, String spareaNm, String blckNo, String ltNo,
                           String naRoadCdNm, String naStdgCdNm, String naGugseCdNm, String naMnLotno,
                           String naSubLotno, String ldgrSeCdNm, String ldgrKindCdNm, String dngNm,
                           String manxSeCdNm, BigDecimal siar, BigDecimal bdar, BigDecimal bdcvrt, BigDecimal gfa,
                           BigDecimal fartCmpttnGfa, BigDecimal fart, String strctCdNm, String etcStrctInfo,
                           String mnUsgCdNm, String etcUsgCn, String roofCdNm, String etcRoofNm, Integer hhCnt,
                           Integer fmlCnt, Integer hoCnt, Integer grndNofl, Integer udgdNofl, BigDecimal hg,
                           Integer psngrElvtrCnt, Integer euseElvtrCnt, Integer anxBdstCnt, BigDecimal anxBdstArea,
                           BigDecimal tolDngGfa, Integer indrMcnclCntom, BigDecimal indrMcnclArea,
                           Integer otdrMcnclCntom, BigDecimal otdrMcnclArea, Integer indrSfprplCntom,
                           BigDecimal indrSfprplArea, Integer otdrSfprplCntom, BigDecimal otdrSfprplArea,
                           LocalDate prmsnYmd, LocalDate bgncstYmd, LocalDate useAprvYmd, String enrgEfcyGrdVl,
                           BigDecimal enrgRtrdt, BigDecimal epiScr, String ecfrdBdstGrdVl, BigDecimal ecfrdBdstCertScr,
                           String intgBdstGrdVl, BigDecimal intgBdstCertScr, String rserDesignAplcnYn,
                           String rserAbltCn) {
        this.bdrgSn = bdrgSn;
        this.platPlc = platPlc;
        this.sggCdNm = sggCdNm;
        this.stdgCdNm = stdgCdNm;
        this.plotSeCdNm = plotSeCdNm;
        this.mnLotno = mnLotno;
        this.subLotno = subLotno;
        this.spareaNm = spareaNm;
        this.blckNo = blckNo;
        this.ltNo = ltNo;
        this.naRoadCdNm = naRoadCdNm;
        this.naStdgCdNm = naStdgCdNm;
        this.naGugseCdNm = naGugseCdNm;
        this.naMnLotno = naMnLotno;
        this.naSubLotno = naSubLotno;
        this.ldgrSeCdNm = ldgrSeCdNm;
        this.ldgrKindCdNm = ldgrKindCdNm;
        this.dngNm = dngNm;
        this.manxSeCdNm = manxSeCdNm;
        this.siar = siar;
        this.bdar = bdar;
        this.bdcvrt = bdcvrt;
        this.gfa = gfa;
        this.fartCmpttnGfa = fartCmpttnGfa;
        this.fart = fart;
        this.strctCdNm = strctCdNm;
        this.etcStrctInfo = etcStrctInfo;
        this.mnUsgCdNm = mnUsgCdNm;
        this.etcUsgCn = etcUsgCn;
        this.roofCdNm = roofCdNm;
        this.etcRoofNm = etcRoofNm;
        this.hhCnt = hhCnt;
        this.fmlCnt = fmlCnt;
        this.hoCnt = hoCnt;
        this.grndNofl = grndNofl;
        this.udgdNofl = udgdNofl;
        this.hg = hg;
        this.psngrElvtrCnt = psngrElvtrCnt;
        this.euseElvtrCnt = euseElvtrCnt;
        this.anxBdstCnt = anxBdstCnt;
        this.anxBdstArea = anxBdstArea;
        this.tolDngGfa = tolDngGfa;
        this.indrMcnclCntom = indrMcnclCntom;
        this.indrMcnclArea = indrMcnclArea;
        this.otdrMcnclCntom = otdrMcnclCntom;
        this.otdrMcnclArea = otdrMcnclArea;
        this.indrSfprplCntom = indrSfprplCntom;
        this.indrSfprplArea = indrSfprplArea;
        this.otdrSfprplCntom = otdrSfprplCntom;
        this.otdrSfprplArea = otdrSfprplArea;
        this.prmsnYmd = prmsnYmd;
        this.bgncstYmd = bgncstYmd;
        this.useAprvYmd = useAprvYmd;
        this.enrgEfcyGrdVl = enrgEfcyGrdVl;
        this.enrgRtrdt = enrgRtrdt;
        this.epiScr = epiScr;
        this.ecfrdBdstGrdVl = ecfrdBdstGrdVl;
        this.ecfrdBdstCertScr = ecfrdBdstCertScr;
        this.intgBdstGrdVl = intgBdstGrdVl;
        this.intgBdstCertScr = intgBdstCertScr;
        this.rserDesignAplcnYn = rserDesignAplcnYn;
        this.rserAbltCn = rserAbltCn;
        this.isDeleted = false;
    }

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
