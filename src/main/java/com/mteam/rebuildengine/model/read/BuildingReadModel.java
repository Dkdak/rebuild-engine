package com.mteam.rebuildengine.model.read;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BuildingReadModel(
        String bdrgSn,
        String platPlc,
        String sggCdNm,
        String stdgCdNm,
        BigDecimal siar,
        BigDecimal bdar,
        BigDecimal bdcvrt,
        BigDecimal gfa,
        BigDecimal fart,
        String strctCdNm,
        String mnUsgCdNm,
        Integer grndNofl,
        Integer udgdNofl,
        Integer hhCnt,
        LocalDate useAprvYmd
) {
}
