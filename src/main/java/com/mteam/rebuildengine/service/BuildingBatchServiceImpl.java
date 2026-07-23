package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.repository.BuildingRepository;
import com.mteam.rebuildengine.utils.SafeParser;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.List;

// 서울열린데이터광장 vBigDjrTitle을 페이지네이션으로 끝까지 조회해 BuildingEntity에 upsert (FEATURE_12_DATA_BATCH.md §B-1)
@Service
@RequiredArgsConstructor
public class BuildingBatchServiceImpl implements BuildingBatchService {

    private static final Logger logger = LogManager.getLogger(BuildingBatchServiceImpl.class);
    private static final int PAGE_SIZE = 1000;

    private final SeoulBuildingClient seoulBuildingClient;
    private final BuildingRepository buildingRepository;

    @Override
    public SyncResult syncRange(int startIndex, int endIndex) {
        SeoulBuildingClient.Page page = seoulBuildingClient.fetchPage(startIndex, endIndex);
        int synced = upsertRows(page.rows());
        return new SyncResult(page.totalCount(), synced);
    }

    @Override
    public SyncResult syncAll() {
        int start = 1;
        int totalCount = Integer.MAX_VALUE;
        int totalSynced = 0;

        while (start <= totalCount) {
            int end = start + PAGE_SIZE - 1;
            SeoulBuildingClient.Page page = seoulBuildingClient.fetchPage(start, end);
            totalCount = page.totalCount();
            totalSynced += upsertRows(page.rows());
            logger.info("건축물대장 배치 진행: {}/{}", Math.min(end, totalCount), totalCount);
            start += PAGE_SIZE;
        }

        return new SyncResult(totalCount, totalSynced);
    }

    // findByBdrgSn()은 자체 트랜잭션이 끝나면 엔티티가 detach되므로, dirty checking에 기대지 않고
    // 매번 명시적으로 save()를 호출해 병합(merge)한다 (self-invocation으로 인한 @Transactional 프록시 우회 문제 회피).
    private int upsertRows(List<JsonNode> rows) {
        int count = 0;
        for (JsonNode row : rows) {
            String bdrgSn = SafeParser.text(row.path("BDRG_SN"));
            if (bdrgSn == null || bdrgSn.isBlank()) {
                continue;
            }

            BuildingEntity fresh = toEntity(row, bdrgSn);
            BuildingEntity toSave = buildingRepository.findByBdrgSn(bdrgSn)
                    .map(existing -> {
                        existing.updateFrom(fresh);
                        return existing;
                    })
                    .orElse(fresh);
            buildingRepository.save(toSave);
            count++;
        }
        return count;
    }

    private BuildingEntity toEntity(JsonNode row, String bdrgSn) {
        return BuildingEntity.builder()
                .bdrgSn(bdrgSn)
                .platPlc(SafeParser.text(row.path("PLAT_PLC")))
                .sggCdNm(SafeParser.text(row.path("SGG_CD_NM")))
                .stdgCdNm(SafeParser.text(row.path("STDG_CD_NM")))
                .plotSeCdNm(SafeParser.text(row.path("PLOT_SE_CD_NM")))
                .mnLotno(SafeParser.text(row.path("MN_LOTNO")))
                .subLotno(SafeParser.text(row.path("SUB_LOTNO")))
                .spareaNm(SafeParser.text(row.path("SPAREA_NM")))
                .blckNo(SafeParser.text(row.path("BLCK_NO")))
                .ltNo(SafeParser.text(row.path("LT_NO")))
                .naRoadCdNm(SafeParser.text(row.path("NA_ROAD_CD_NM")))
                .naStdgCdNm(SafeParser.text(row.path("NA_STDG_CD_NM")))
                .naGugseCdNm(SafeParser.text(row.path("NA_GUGSE_CD_NM")))
                .naMnLotno(SafeParser.text(row.path("NA_MN_LOTNO")))
                .naSubLotno(SafeParser.text(row.path("NA_SUB_LOTNO")))
                .ldgrSeCdNm(SafeParser.text(row.path("LDGR_SE_CD_NM")))
                .ldgrKindCdNm(SafeParser.text(row.path("LDGR_KIND_CD_NM")))
                .dngNm(SafeParser.text(row.path("DNG_NM")))
                .manxSeCdNm(SafeParser.text(row.path("MANX_SE_CD_NM")))
                .siar(SafeParser.toBigDecimal(SafeParser.text(row.path("SIAR"))))
                .bdar(SafeParser.toBigDecimal(SafeParser.text(row.path("BDAR"))))
                .bdcvrt(SafeParser.toBigDecimal(SafeParser.text(row.path("BDCVRT"))))
                .gfa(SafeParser.toBigDecimal(SafeParser.text(row.path("GFA"))))
                .fartCmpttnGfa(SafeParser.toBigDecimal(SafeParser.text(row.path("FART_CMPTTN_GFA"))))
                .fart(SafeParser.toBigDecimal(SafeParser.text(row.path("FART"))))
                .strctCdNm(SafeParser.text(row.path("STRCT_CD_NM")))
                .etcStrctInfo(SafeParser.text(row.path("ETC_STRCT_INFO")))
                .mnUsgCdNm(SafeParser.text(row.path("MN_USG_CD_NM")))
                .etcUsgCn(SafeParser.text(row.path("ETC_USG_CN")))
                .roofCdNm(SafeParser.text(row.path("ROOF_CD_NM")))
                .etcRoofNm(SafeParser.text(row.path("ETC_ROOF_NM")))
                .hhCnt(SafeParser.toInteger(SafeParser.text(row.path("HH_CNT"))))
                .fmlCnt(SafeParser.toInteger(SafeParser.text(row.path("FML_CNT"))))
                .hoCnt(SafeParser.toInteger(SafeParser.text(row.path("HO_CNT"))))
                .grndNofl(SafeParser.toInteger(SafeParser.text(row.path("GRND_NOFL"))))
                .udgdNofl(SafeParser.toInteger(SafeParser.text(row.path("UDGD_NOFL"))))
                .hg(SafeParser.toBigDecimal(SafeParser.text(row.path("HG"))))
                .psngrElvtrCnt(SafeParser.toInteger(SafeParser.text(row.path("PSNGR_ELVTR_CNT"))))
                .euseElvtrCnt(SafeParser.toInteger(SafeParser.text(row.path("EUSE_ELVTR_CNT"))))
                .anxBdstCnt(SafeParser.toInteger(SafeParser.text(row.path("ANX_BDST_CNT"))))
                .anxBdstArea(SafeParser.toBigDecimal(SafeParser.text(row.path("ANX_BDST_AREA"))))
                .tolDngGfa(SafeParser.toBigDecimal(SafeParser.text(row.path("TOL_DNG_GFA"))))
                .indrMcnclCntom(SafeParser.toInteger(SafeParser.text(row.path("INDR_MCNCL_CNTOM"))))
                .indrMcnclArea(SafeParser.toBigDecimal(SafeParser.text(row.path("INDR_MCNCL_AREA"))))
                .otdrMcnclCntom(SafeParser.toInteger(SafeParser.text(row.path("OTDR_MCNCL_CNTOM"))))
                .otdrMcnclArea(SafeParser.toBigDecimal(SafeParser.text(row.path("OTDR_MCNCL_AREA"))))
                .indrSfprplCntom(SafeParser.toInteger(SafeParser.text(row.path("INDR_SFPRPL_CNTOM"))))
                .indrSfprplArea(SafeParser.toBigDecimal(SafeParser.text(row.path("INDR_SFPRPL_AREA"))))
                .otdrSfprplCntom(SafeParser.toInteger(SafeParser.text(row.path("OTDR_SFPRPL_CNTOM"))))
                .otdrSfprplArea(SafeParser.toBigDecimal(SafeParser.text(row.path("OTDR_SFPRPL_AREA"))))
                .prmsnYmd(SafeParser.toDate(SafeParser.text(row.path("PRMSN_YMD"))))
                .bgncstYmd(SafeParser.toDate(SafeParser.text(row.path("BGNCST_YMD"))))
                .useAprvYmd(SafeParser.toDate(SafeParser.text(row.path("USE_APRV_YMD"))))
                .enrgEfcyGrdVl(SafeParser.text(row.path("ENRG_EFCY_GRD_VL")))
                .enrgRtrdt(SafeParser.toBigDecimal(SafeParser.text(row.path("ENRG_RTRDT"))))
                .epiScr(SafeParser.toBigDecimal(SafeParser.text(row.path("EPI_SCR"))))
                .ecfrdBdstGrdVl(SafeParser.text(row.path("ECFRD_BDST_GRD_VL")))
                .ecfrdBdstCertScr(SafeParser.toBigDecimal(SafeParser.text(row.path("ECFRD_BDST_CERT_SCR"))))
                .intgBdstGrdVl(SafeParser.text(row.path("INTG_BDST_GRD_VL")))
                .intgBdstCertScr(SafeParser.toBigDecimal(SafeParser.text(row.path("INTG_BDST_CERT_SCR"))))
                .rserDesignAplcnYn(SafeParser.text(row.path("RSER_DESIGN_APLCN_YN")))
                .rserAbltCn(SafeParser.text(row.path("RSER_ABLT_CN")))
                .build();
    }
}
