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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
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

    // 배치 재실행 시 최신 응답으로 캐시를 갱신 (dirty checking, FEATURE_12_DATA_BATCH.md §B-1)
    public void updateFrom(BuildingEntity fresh) {
        this.platPlc = fresh.platPlc;
        this.sggCdNm = fresh.sggCdNm;
        this.stdgCdNm = fresh.stdgCdNm;
        this.plotSeCdNm = fresh.plotSeCdNm;
        this.mnLotno = fresh.mnLotno;
        this.subLotno = fresh.subLotno;
        this.spareaNm = fresh.spareaNm;
        this.blckNo = fresh.blckNo;
        this.ltNo = fresh.ltNo;
        this.naRoadCdNm = fresh.naRoadCdNm;
        this.naStdgCdNm = fresh.naStdgCdNm;
        this.naGugseCdNm = fresh.naGugseCdNm;
        this.naMnLotno = fresh.naMnLotno;
        this.naSubLotno = fresh.naSubLotno;
        this.ldgrSeCdNm = fresh.ldgrSeCdNm;
        this.ldgrKindCdNm = fresh.ldgrKindCdNm;
        this.dngNm = fresh.dngNm;
        this.manxSeCdNm = fresh.manxSeCdNm;
        this.siar = fresh.siar;
        this.bdar = fresh.bdar;
        this.bdcvrt = fresh.bdcvrt;
        this.gfa = fresh.gfa;
        this.fartCmpttnGfa = fresh.fartCmpttnGfa;
        this.fart = fresh.fart;
        this.strctCdNm = fresh.strctCdNm;
        this.etcStrctInfo = fresh.etcStrctInfo;
        this.mnUsgCdNm = fresh.mnUsgCdNm;
        this.etcUsgCn = fresh.etcUsgCn;
        this.roofCdNm = fresh.roofCdNm;
        this.etcRoofNm = fresh.etcRoofNm;
        this.hhCnt = fresh.hhCnt;
        this.fmlCnt = fresh.fmlCnt;
        this.hoCnt = fresh.hoCnt;
        this.grndNofl = fresh.grndNofl;
        this.udgdNofl = fresh.udgdNofl;
        this.hg = fresh.hg;
        this.psngrElvtrCnt = fresh.psngrElvtrCnt;
        this.euseElvtrCnt = fresh.euseElvtrCnt;
        this.anxBdstCnt = fresh.anxBdstCnt;
        this.anxBdstArea = fresh.anxBdstArea;
        this.tolDngGfa = fresh.tolDngGfa;
        this.indrMcnclCntom = fresh.indrMcnclCntom;
        this.indrMcnclArea = fresh.indrMcnclArea;
        this.otdrMcnclCntom = fresh.otdrMcnclCntom;
        this.otdrMcnclArea = fresh.otdrMcnclArea;
        this.indrSfprplCntom = fresh.indrSfprplCntom;
        this.indrSfprplArea = fresh.indrSfprplArea;
        this.otdrSfprplCntom = fresh.otdrSfprplCntom;
        this.otdrSfprplArea = fresh.otdrSfprplArea;
        this.prmsnYmd = fresh.prmsnYmd;
        this.bgncstYmd = fresh.bgncstYmd;
        this.useAprvYmd = fresh.useAprvYmd;
        this.enrgEfcyGrdVl = fresh.enrgEfcyGrdVl;
        this.enrgRtrdt = fresh.enrgRtrdt;
        this.epiScr = fresh.epiScr;
        this.ecfrdBdstGrdVl = fresh.ecfrdBdstGrdVl;
        this.ecfrdBdstCertScr = fresh.ecfrdBdstCertScr;
        this.intgBdstGrdVl = fresh.intgBdstGrdVl;
        this.intgBdstCertScr = fresh.intgBdstCertScr;
        this.rserDesignAplcnYn = fresh.rserDesignAplcnYn;
        this.rserAbltCn = fresh.rserAbltCn;
    }
}
