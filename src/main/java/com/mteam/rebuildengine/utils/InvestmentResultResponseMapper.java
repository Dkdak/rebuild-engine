package com.mteam.rebuildengine.utils;

import com.mteam.rebuildengine.model.entity.InvestmentResultEntity;
import com.mteam.rebuildengine.model.response.EstimatedPriceResponse;
import com.mteam.rebuildengine.model.response.MarketAnalysisResponse;
import com.mteam.rebuildengine.model.response.RemodelingResultResponse;
import tools.jackson.databind.ObjectMapper;

// investment_result의 remodeling_basis/market_basis(JSON)에서 verdict·estimatedPrice만 꺼내는 변환 —
// PropertyServiceImpl(F-04 목록)과 FavoriteServiceImpl(F-11 관심목록)이 같은 소스에서 같은 값을
// 뽑아 쓰므로 공용 유틸로 뺀다. 새 계산 없음, 저장된 값 그대로 부분 파싱.
public final class InvestmentResultResponseMapper {

    private InvestmentResultResponseMapper() {
    }

    public static String verdict(InvestmentResultEntity result, ObjectMapper objectMapper) {
        if (result == null || result.getRemodelingBasis() == null) {
            return null;
        }
        RemodelingResultResponse remodeling = objectMapper.readValue(result.getRemodelingBasis(), RemodelingResultResponse.class);
        return remodeling.verdict() != null ? remodeling.verdict().name() : null;
    }

    public static EstimatedPriceResponse estimatedPrice(InvestmentResultEntity result, ObjectMapper objectMapper) {
        if (result == null || result.getMarketBasis() == null) {
            return null;
        }
        MarketAnalysisResponse market = objectMapper.readValue(result.getMarketBasis(), MarketAnalysisResponse.class);
        return market.estimatedPrice();
    }
}
