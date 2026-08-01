package com.mteam.rebuildengine.model.response;

import com.mteam.rebuildengine.model.entity.TradeEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

// F-04 §2.1-h / F-05 §5.2 "최근 실거래가" 공용 필드 — BuildingInfoResponse에 실려서 F-04(PropertyResponse)와
// F-05(buildings/title) 양쪽이 같은 값을 쓴다. trade.building_id 매핑(F-15 §3.4) 결과로 채워지며,
// 매칭 안 되는 건물은 BuildingInfoResponse.recentTrade() 자체가 null.
public record RecentTradeResponse(BigDecimal price, BigDecimal area, LocalDate contractDate) {
    public static RecentTradeResponse from(TradeEntity trade) {
        return new RecentTradeResponse(trade.getPrice10kWon(), trade.getAreaSqm(), trade.getContractDate());
    }
}
