package com.mteam.rebuildengine.service.analysis;

import com.mteam.rebuildengine.mapper.MarketComparableCondition;
import com.mteam.rebuildengine.mapper.MarketMapper;
import com.mteam.rebuildengine.mapper.PricePositionRankCondition;
import com.mteam.rebuildengine.mapper.TradeActivityCondition;
import com.mteam.rebuildengine.model.entity.ApartmentPriceEntity;
import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.model.entity.DetachedHousePriceEntity;
import com.mteam.rebuildengine.model.entity.LandPriceEntity;
import com.mteam.rebuildengine.model.entity.TradeEntity;
import com.mteam.rebuildengine.model.response.AgeAdjustedPriceResponse;
import com.mteam.rebuildengine.model.read.ComparableTradeSampleReadModel;
import com.mteam.rebuildengine.model.read.ComparableTradeSearchResult;
import com.mteam.rebuildengine.model.read.ComparableTradeStatsReadModel;
import com.mteam.rebuildengine.model.read.PriceTrendPointReadModel;
import com.mteam.rebuildengine.model.read.TradeActivityReadModel;
import com.mteam.rebuildengine.model.response.ComparableTradeResponse;
import com.mteam.rebuildengine.model.response.ConfidenceLevel;
import com.mteam.rebuildengine.model.response.EstimatedPriceResponse;
import com.mteam.rebuildengine.model.response.MarketAnalysisResponse;
import com.mteam.rebuildengine.model.response.PriceTrendPointResponse;
import com.mteam.rebuildengine.model.response.PriceTrendResponse;
import com.mteam.rebuildengine.model.response.PricePositionResponse;
import com.mteam.rebuildengine.model.response.RecentTradeResponse;
import com.mteam.rebuildengine.model.response.RemodelingBasisResponse;
import com.mteam.rebuildengine.model.response.RemodelingResultResponse;
import com.mteam.rebuildengine.model.response.RemodelingVerdict;
import com.mteam.rebuildengine.model.response.TradeActivityResponse;
import com.mteam.rebuildengine.repository.ApartmentPriceRepository;
import com.mteam.rebuildengine.repository.BuildingRepository;
import com.mteam.rebuildengine.repository.DetachedHousePriceRepository;
import com.mteam.rebuildengine.repository.LandPriceRepository;
import com.mteam.rebuildengine.repository.TradeRepository;
import com.mteam.rebuildengine.utils.PropertyType;
import com.mteam.rebuildengine.utils.PropertyTypeClassifier;
import com.mteam.rebuildengine.utils.RepresentativePriceCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// FEATURE_08_MARKET.md §3.4~§3.6 — "최근 실거래가"(매칭, A)·"추정 시세"(유사 거래 비교+단계적 완화,
// B)·공시가격·토지당 가격을 한 화면(F-05 시세분석 섹션)에 조합한다. 자체 배치 없이 F-15/F-16이 채운
// trade/apartment_price/land_price만 조회(§3.2).
@Service
@RequiredArgsConstructor
public class MarketServiceImpl implements MarketService {

    // §3.4-B 유사 거래 기준(면적 ±비율, 연식 ±년) — FEATURE_08_MARKET.md §3.6(2026-08-08, trade 실측
    // 검증 후 확정)이 정한 값. 원래 초기값(0/1단계 ±20%, 2단계 ±35%)에서 좁혔다 — 2026-08-10 발견:
    // 문서만 갱신되고 이 상수는 그대로 남아있던 걸 뒤늦게 반영(프론트 확인 중 발견).
    private static final BigDecimal STAGE_AREA_RATIO = BigDecimal.valueOf(0.10);
    private static final int STAGE_BUILD_YEAR_RANGE = 5;
    private static final BigDecimal WIDENED_AREA_RATIO = BigDecimal.valueOf(0.20);
    private static final int WIDENED_BUILD_YEAR_RANGE = 10;
    // §3.4-B-3 "시점 보정"의 단순화 버전 — 최근 3년 이내 거래만 비교 대상으로 삼는다(가격 통계용).
    private static final long RECENCY_WINDOW_MONTHS = 36;
    // §8.17 "거래 활성도"용 — 가격 통계보다 넓은 5년 창이 필요해 별도 인덱스(loadTradeActivityIndex)로 로드.
    private static final long TRADE_ACTIVITY_WINDOW_MONTHS = 60;
    // 이보다 적으면 중앙값이 불안정하다고 보고 다음 완화 단계로 넘어간다.
    private static final int MIN_COMPARABLE_COUNT = 3;
    // §3.8 "시세 추이" — 36개월 중 유효 월이 이보다 적으면 꺾은선 그래프로서 의미가 부족하다고 보고
    // 법정동→구로 완화한다(backend 잠정치, MIN_COMPARABLE_COUNT와 같은 성격 — 실측 검증 전).
    private static final int MIN_TREND_MONTHS = 6;

    private final BuildingRepository buildingRepository;
    private final TradeRepository tradeRepository;
    private final ApartmentPriceRepository apartmentPriceRepository;
    private final LandPriceRepository landPriceRepository;
    private final DetachedHousePriceRepository detachedHousePriceRepository;
    private final MarketMapper marketMapper;
    private final RemodelingService remodelingService;

    @Override
    public Optional<MarketAnalysisResponse> getMarketAnalysis(String buildingId) {
        return buildingRepository.findByBdrgSnAndIsAncillaryFalseAndIsOutOfScopeFalseAndIsDeletedFalse(buildingId).flatMap(building ->
                remodelingService.getRemodelingResult(building.getBdrgSn())
                        .map(remodeling -> getMarketAnalysis(building, remodeling)));
    }

    @Override
    public MarketAnalysisResponse getMarketAnalysis(BuildingEntity building, RemodelingResultResponse remodeling) {
        return buildMarketAnalysis(building, remodeling,
                findRecentTrade(building.getBdrgSn()),
                latestOfficialPrice(building.getBdrgSn()),
                latestLandPrice(building.getBdrgSn()));
    }

    // F-09 V1 배치(InvestmentAnalysisBatchService) — 페이지 단위로 미리 벌크 조회한 값(recentTrade/
    // apartmentPrice/landPrice)은 그대로 쓰되, F-08 유사거래 비교(추정 시세)는 DB를 다시 묻지 않고
    // TradeStatsIndex(배치 시작 시 1회 로드)에서 계산한다 — 건물당 최대 6번이던 DB 왕복을 없앤다
    // (2026-08-08, 실측 후 도입). 계산 로직(3단계 완화·중앙값 산식) 자체는 estimatePriceForArea()를
    // 그대로 공유해서 라이브 조회와 결과가 갈리지 않는다. tradeActivityIndex(§8.17, 2026-08-09 추가)는
    // 가격 통계용 tradeStatsIndex와 별개로 5년 창으로 로드된 인덱스 — "거래 활성도" 카운트 전용.
    @Override
    public MarketAnalysisResponse getMarketAnalysis(BuildingEntity building, RemodelingResultResponse remodeling,
                                                      BuildingDataBundle bundle, TradeStatsIndex tradeStatsIndex,
                                                      TradeStatsIndex tradeActivityIndex) {
        RecentTradeResponse recentTrade = toRecentTrade(bundle.recentTrades(building.getBdrgSn()));
        CurrentEstimate current = estimateCurrentPriceAndTrend(building, recentTrade, tradeStatsIndex, tradeActivityIndex);
        return new MarketAnalysisResponse(
                recentTrade,
                current.price(),
                latestOfficialPriceFromRows(bundle.apartmentPrices(building.getBdrgSn()),
                        bundle.detachedHousePrices(building.getBdrgSn())),
                latestLandPriceFromRows(bundle.landPrices(building.getBdrgSn())),
                estimatePostRemodelPrice(building, remodeling, current.price(), tradeStatsIndex),
                current.trend(),
                current.tradeActivity(),
                current.pricePosition());
    }

    // FEATURE_10_MARKET.md CASE2(2026-08-27) — "시장 내 가격 위치"를 실측 매입가(만원 단위 총액, F-19
    // ACTUAL_PURCHASE_PRICE) 기준으로 재계산한다. 세대 기반 유형(아파트/연립다세대)은 매입가가 건물
    // 전체(hh_cnt세대) 값이라 세대수로 먼저 나눠 세대당 가격으로 바꾼 뒤 targetArea(세대당 면적)로
    // ㎡당가를 구한다 — computeRecalculation()이 currentValue를 만들 때 쓰는 것과 반대 방향의 환산
    // (MeasurementServiceImpl §3.3 "입력 소스만 바꿔치기"와 같은 매입가를 공유해야 두 화면이 안 갈린다).
    // 그 외 유형은 targetArea가 이미 건물 전체 면적이라 바로 나눈다. 모집단(p25/median/p75)은 건드리지
    // 않는다 — estimatePriceForArea의 기존 캐스케이드를 그대로 타서 공공데이터 기준과 완전히 같은 단계를
    // 쓴다(04 문서 확정).
    @Override
    public Optional<PricePositionResponse> getMeasuredPricePosition(BuildingEntity building, BigDecimal measuredPurchasePrice) {
        Optional<PropertyType> type = PropertyTypeClassifier.classify(building.getMnUsgCdNm(), building.getGrndNofl());
        if (type.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal targetArea = PropertyTypeClassifier.displayArea(type.get(), building.getGfa(), building.getHhCnt());
        if (targetArea == null || targetArea.signum() <= 0) {
            return Optional.empty();
        }
        boolean householdBased = type.get() == PropertyType.APARTMENT || type.get() == PropertyType.ROW_HOUSE;
        if (householdBased && (building.getHhCnt() == null || building.getHhCnt() == 0)) {
            return Optional.empty();
        }
        BigDecimal areaBase = householdBased ? targetArea.multiply(BigDecimal.valueOf(building.getHhCnt())) : targetArea;
        BigDecimal overridePricePerSqm = measuredPurchasePrice.divide(areaBase, 10, RoundingMode.HALF_UP);
        Integer buildYear = building.getUseAprvYmd() == null ? null : building.getUseAprvYmd().getYear();
        PriceEstimate estimate = estimatePriceForArea(building, type.get(), targetArea, buildYear, null, null, null,
                true, overridePricePerSqm);
        return Optional.ofNullable(estimate.pricePosition());
    }

    @Override
    public TradeStatsIndex loadTradeStatsIndex() {
        LocalDate recencyCutoff = LocalDate.now().minusMonths(RECENCY_WINDOW_MONTHS);
        return TradeStatsIndex.load(marketMapper.findAllComparableTradeRows(recencyCutoff));
    }

    // §8.17 "거래 활성도" 전용 — 가격 통계용 인덱스(36개월)와 별개로 60개월(5년) 창으로 로드한다.
    // TradeStatsIndex 구조(버킷팅·필터링)는 완전히 재사용하되 인스턴스만 분리해서, 가격 통계 쪽 필터
    // 범위(recency)에는 영향을 주지 않는다.
    @Override
    public TradeStatsIndex loadTradeActivityIndex() {
        LocalDate recencyCutoff = LocalDate.now().minusMonths(TRADE_ACTIVITY_WINDOW_MONTHS);
        return TradeStatsIndex.load(marketMapper.findAllComparableTradeRows(recencyCutoff));
    }

    private MarketAnalysisResponse buildMarketAnalysis(BuildingEntity building, RemodelingResultResponse remodeling,
                                                         RecentTradeResponse recentTrade, BigDecimal officialPrice,
                                                         BigDecimal landPrice) {
        CurrentEstimate current = estimateCurrentPriceAndTrend(building, recentTrade, null, null);
        return new MarketAnalysisResponse(recentTrade, current.price(), officialPrice, landPrice,
                estimatePostRemodelPrice(building, remodeling, current.price(), null),
                current.trend(), current.tradeActivity(), current.pricePosition());
    }

    // §3.4-A "이 건물의 최근 실거래가" — trade.building_id 매칭(F-15 §3.4)이 안 된 건물이거나 단독다가구/
    // 상업업무용/공장창고(매칭 자체가 안 되는 유형, §3.3)면 자연히 빈 값.
    private RecentTradeResponse findRecentTrade(String buildingId) {
        List<TradeEntity> trades = tradeRepository.findByBuildingIdInAndCancelDateIsNullOrderByContractDateDesc(
                List.of(buildingId));
        return toRecentTrade(trades);
    }

    private static RecentTradeResponse toRecentTrade(List<TradeEntity> trades) {
        return trades.isEmpty() ? null : RecentTradeResponse.from(trades.get(0));
    }

    // §3.4-B/§3.5 — 법정동(0단계) → 구 전체(1단계) → 범위 확대(2단계) 순으로 완화하며 유사 거래를
    // 찾는다. 3단계(공시가격 기반 근사)는 검증된 비율이 없어 구현하지 않고 바로 4단계(추정 불가)로 간다.
    // §3.8 "시세 추이"도 0/1단계는 같은 조건이라 여기서 같이 계산한다(TrendCollector, 성능 개선).
    // tradeActivity/pricePosition(§8.17)도 여기서 함께 계산 — recentTrade/targetArea가 이미 있어야
    // 하는 계산이라 estimateCurrentPriceAndTrend가 자연스러운 위치.
    private record CurrentEstimate(EstimatedPriceResponse price, PriceTrendResponse trend,
                                    TradeActivityResponse tradeActivity, PricePositionResponse pricePosition) {
    }

    // tradeStatsIndex가 있으면(배치) 메모리에서, 없으면(라이브 단건 조회) DB에서 계산한다.
    private CurrentEstimate estimateCurrentPriceAndTrend(BuildingEntity building, RecentTradeResponse recentTrade,
                                                          TradeStatsIndex tradeStatsIndex, TradeStatsIndex tradeActivityIndex) {
        Optional<PropertyType> type = PropertyTypeClassifier.classify(building.getMnUsgCdNm(), building.getGrndNofl());
        if (type.isEmpty()) {
            return new CurrentEstimate(EstimatedPriceResponse.unavailable(), null, null, null);
        }
        BigDecimal targetArea = PropertyTypeClassifier.displayArea(type.get(), building.getGfa(), building.getHhCnt());
        if (targetArea == null || targetArea.signum() <= 0) {
            return new CurrentEstimate(EstimatedPriceResponse.unavailable(), null, null, null);
        }
        Integer buildYear = building.getUseAprvYmd() == null ? null : building.getUseAprvYmd().getYear();
        TrendCollector trendCollector = new TrendCollector();
        PriceEstimate estimate = estimatePriceForArea(building, type.get(), targetArea, buildYear, tradeStatsIndex,
                trendCollector, recentTrade, true);
        TradeActivityResponse tradeActivity = tradeActivity(building, type.get(), targetArea, tradeActivityIndex);
        return new CurrentEstimate(estimate.price(), trendCollector.trend, tradeActivity, estimate.pricePosition());
    }

    // §8.17 "거래 활성도" — estimatedPrice §3.4-B SAME_DONG 단계(0단계)와 완전히 같은 필터(법정동×유형×
    // 면적±20%×연식±5)로 최근 1/3/5년 거래건수를 센다. 완화 단계를 타지 않는 고정 모집단 — "이 매물과
    // 거의 동일한 조건의 거래가 최근 시장에 얼마나 있었나"를 보여주는 지표라 SAME_DONG 하나로 충분.
    private TradeActivityResponse tradeActivity(BuildingEntity building, PropertyType type, BigDecimal targetArea,
                                                 TradeStatsIndex tradeActivityIndex) {
        Integer buildYear = building.getUseAprvYmd() == null ? null : building.getUseAprvYmd().getYear();
        LocalDate cutoff1y = LocalDate.now().minusYears(1);
        LocalDate cutoff3y = LocalDate.now().minusYears(3);
        LocalDate cutoff5y = LocalDate.now().minusYears(5);
        String bjdongNm = building.getStdgCdNm();
        BigDecimal areaMin = rangeMin(targetArea, STAGE_AREA_RATIO);
        BigDecimal areaMax = rangeMax(targetArea, STAGE_AREA_RATIO);
        Integer buildYearMin = rangeMin(buildYear, STAGE_BUILD_YEAR_RANGE);
        Integer buildYearMax = rangeMax(buildYear, STAGE_BUILD_YEAR_RANGE);

        if (tradeActivityIndex != null) {
            TradeStatsIndex.ActivityCounts counts = tradeActivityIndex.tradeActivityCounts(type.label(),
                    building.getSggCdNm(), bjdongNm, areaMin, areaMax, buildYearMin, buildYearMax,
                    cutoff1y, cutoff3y, cutoff5y);
            return new TradeActivityResponse(counts.recent1yCount(), counts.recent3yCount(), counts.recent5yCount());
        }
        TradeActivityCondition condition = new TradeActivityCondition(type.label(), building.getSggCdNm(), bjdongNm,
                areaMin, areaMax, buildYearMin, buildYearMax, cutoff1y, cutoff3y, cutoff5y);
        TradeActivityReadModel counts = marketMapper.findTradeActivityCounts(condition);
        return new TradeActivityResponse((int) counts.recent1yCount(), (int) counts.recent3yCount(), (int) counts.recent5yCount());
    }

    // §3.7 "리모델링 후 예상 시세" — 유형별 분기(2026-08-08 정정). 세대 기반 유형(아파트/연립다세대)은
    // §3.6과 같은 세대당 면적을 그대로 쓰고 세대수 증가만 반영, 그 외(단일 소유 단위)는 §3.4-B와 같은
    // 로직·완화 단계를 재사용하되 비교 기준 면적만 증축 후 면적으로 바꾼다. F-06 "불가" 판정이거나
    // 유형별 참조 필드(세대 기반=estimatedAdditionalHouseholds, 그 외=additionalBuildableAreaSqm)가
    // 없으면 계산 자체를 하지 않고 null. confidenceLevel이 UNAVAILABLE이면 estimatedPrice와 달리 그
    // 객체를 내려보내지 않고 필드 자체를 null로 접는다 — 절반만 계산된 값을 노출하지 않기 위함.
    // currentEstimate(estimatePrice 결과)를 파라미터로 받는다 — 세대 기반 유형은 세대당 면적이 동일해
    // §3.6과 완전히 같은 유사거래 쿼리를 또 돌릴 필요가 없다(중복 실행 버그, 2026-08-08 발견·제거).
    private EstimatedPriceResponse estimatePostRemodelPrice(
            BuildingEntity building, RemodelingResultResponse remodeling, EstimatedPriceResponse currentEstimate,
            TradeStatsIndex tradeStatsIndex) {
        if (remodeling.verdict() == RemodelingVerdict.NOT_POSSIBLE) {
            return null;
        }
        Optional<PropertyType> type = PropertyTypeClassifier.classify(building.getMnUsgCdNm(), building.getGrndNofl());
        if (type.isEmpty()) {
            return null;
        }
        RemodelingBasisResponse basis = remodeling.basis();
        boolean householdBased = type.get() == PropertyType.APARTMENT || type.get() == PropertyType.ROW_HOUSE;
        return householdBased
                ? estimatePostRemodelPriceByHouseholdGrowth(currentEstimate, building.getHhCnt(), basis)
                : estimatePostRemodelPriceByAreaGrowth(building, type.get(), basis, tradeStatsIndex);
    }

    // 아파트/연립다세대 — 세대당 면적은 바꾸지 않고, 세대수 증가만 반영: 예상 가치 = 세대당 추정시세 ×
    // (현재 hh_cnt + estimatedAdditionalHouseholds). §3.6의 estimatedPrice와 비교 기준 면적이 완전히
    // 같아 유사거래 쿼리를 다시 돌리지 않고 그 결과를 그대로 재사용한다.
    private static EstimatedPriceResponse estimatePostRemodelPriceByHouseholdGrowth(
            EstimatedPriceResponse currentEstimate, Integer currentHouseholds, RemodelingBasisResponse basis) {
        Integer additionalHouseholds = basis.estimatedAdditionalHouseholds();
        if (additionalHouseholds == null || currentHouseholds == null) {
            return null;
        }
        if (currentEstimate.confidenceLevel() == ConfidenceLevel.UNAVAILABLE) {
            return null;
        }
        BigDecimal householdFactor = BigDecimal.valueOf(currentHouseholds + additionalHouseholds);
        BigDecimal projectedValue = currentEstimate.value().multiply(householdFactor);
        return new EstimatedPriceResponse(projectedValue, currentEstimate.confidenceLevel(), currentEstimate.comparableCount(),
                currentEstimate.comparableTrades(),
                scaleOrNull(currentEstimate.conservativeValue(), householdFactor),
                scaleOrNull(currentEstimate.optimisticValue(), householdFactor));
    }

    private static BigDecimal scaleOrNull(BigDecimal value, BigDecimal factor) {
        return value == null ? null : value.multiply(factor);
    }

    // 단독다가구/상업업무용/공장창고 — §3.4-B와 완전히 같은 로직·완화 단계를 재사용하되 비교 기준 면적만
    // 증축 후 면적(현재 면적 + additionalBuildableAreaSqm)으로 바꾼다. pricePosition은 현재가 전용
    // 지표라(§8.17) 여기선 계산하지 않는다(needsPricePosition=false).
    private EstimatedPriceResponse estimatePostRemodelPriceByAreaGrowth(
            BuildingEntity building, PropertyType type, RemodelingBasisResponse basis, TradeStatsIndex tradeStatsIndex) {
        BigDecimal additionalBuildableAreaSqm = basis.additionalBuildableAreaSqm();
        if (additionalBuildableAreaSqm == null) {
            return null;
        }
        BigDecimal currentArea = PropertyTypeClassifier.displayArea(type, building.getGfa(), building.getHhCnt());
        if (currentArea == null || currentArea.signum() <= 0) {
            return null;
        }
        BigDecimal postRemodelArea = currentArea.add(additionalBuildableAreaSqm);
        Integer buildYear = building.getUseAprvYmd() == null ? null : building.getUseAprvYmd().getYear();
        PriceEstimate result = estimatePriceForArea(building, type, postRemodelArea, buildYear, tradeStatsIndex, null, null, false);
        return result.price().confidenceLevel() == ConfidenceLevel.UNAVAILABLE ? null : result.price();
    }

    // FEATURE_19_PERSONALIZED_ANALYSIS.md §2.2-a "유효연식 참고표"(FEATURE_08_MARKET.md §3.7 확장,
    // 2026-08-24) — 보정 없음/−10/−15/−20년 4행. estimatePostRemodelPrice와 완전히 같은 유형별 분기를
    // 쓰되, buildYear만 실제 준공연도 + adjustmentYears로 바꿔 같은 유사거래 쿼리를 재실행한다("입력
    // 면적만 다른 같은 쿼리"였던 §3.7에 "입력 연식만 다른 같은 쿼리"를 더한 구조).
    private static final List<Integer> AGE_ADJUSTMENT_YEARS = List.of(0, 10, 15, 20);
    // FEATURE_19 §5.1 "참고표 표본 하한 10건" — backend 관측 7건 기준 잠정치(aging_factor.k와 같은 성격,
    // 실측 사례 축적 후 재보정 대상).
    private static final int REFERENCE_TABLE_MIN_SAMPLE = 10;

    @Override
    public List<AgeAdjustedPriceResponse> getPostRemodelPriceByAge(String buildingId) {
        return buildingRepository.findByBdrgSnAndIsAncillaryFalseAndIsOutOfScopeFalseAndIsDeletedFalse(buildingId)
                .flatMap(building -> remodelingService.getRemodelingResult(building.getBdrgSn())
                        .map(remodeling -> computeAgeAdjustedPrices(building, remodeling)))
                .orElse(List.of());
    }

    // 4행 각각을 먼저 평소처럼(캐스케이드 완화) 계산해 자연 완화 단계를 확인한 뒤, 가장 넓은 단계로
    // 전부 통일해 재조회한다(2026-08-24 재확정, product) — "입력 연식만 다른 같은 쿼리"라는 전제를
    // 지키려면 지역 범위(완화 단계)까지 행마다 달라지면 비교가 성립하지 않기 때문. 이미 가장 넓은
    // 단계에 있던 행은 재조회하지 않는다.
    private List<AgeAdjustedPriceResponse> computeAgeAdjustedPrices(BuildingEntity building, RemodelingResultResponse remodeling) {
        if (remodeling.verdict() == RemodelingVerdict.NOT_POSSIBLE) {
            return List.of();
        }
        Optional<PropertyType> type = PropertyTypeClassifier.classify(building.getMnUsgCdNm(), building.getGrndNofl());
        if (type.isEmpty()) {
            return List.of();
        }
        RemodelingBasisResponse basis = remodeling.basis();
        boolean householdBased = type.get() == PropertyType.APARTMENT || type.get() == PropertyType.ROW_HOUSE;
        Integer actualBuildYear = building.getUseAprvYmd() == null ? null : building.getUseAprvYmd().getYear();

        List<RawAgeEstimate> raws = AGE_ADJUSTMENT_YEARS.stream()
                .map(years -> householdBased
                        ? rawByHouseholdGrowth(building, type.get(), basis, actualBuildYear, years)
                        : rawByAreaGrowth(building, type.get(), basis, actualBuildYear, years))
                .filter(java.util.Objects::nonNull)
                .toList();
        // §3.7과 동일한 원칙 — 4행 중 하나라도 basis 부재로 계산 불가면 부분 참고표를 내려보내지 않는다.
        if (raws.size() < AGE_ADJUSTMENT_YEARS.size()) {
            return List.of();
        }

        int widestStage = raws.stream()
                .mapToInt(r -> stageForConfidence(r.unitEstimate().price().confidenceLevel()))
                .max().orElse(MATCH_STAGE_GU_TYPE_AVERAGE);

        return raws.stream().map(r -> {
            int rowStage = stageForConfidence(r.unitEstimate().price().confidenceLevel());
            PriceEstimate unified = rowStage == widestStage ? r.unitEstimate()
                    : estimatePriceForAreaAtStage(building, type.get(), r.targetArea(),
                            actualBuildYear == null ? null : actualBuildYear + r.adjustmentYears(), widestStage);
            EstimatedPriceResponse finalPrice = r.householdFactor() != null
                    ? applyHouseholdFactor(unified.price(), r.householdFactor())
                    : unified.price();
            boolean insufficientSample = finalPrice.comparableCount() < REFERENCE_TABLE_MIN_SAMPLE;
            return new AgeAdjustedPriceResponse(r.adjustmentYears(), r.targetArea(), finalPrice, insufficientSample);
        }).toList();
    }

    // householdFactor가 null이면 면적기반 유형(단독다가구·상업업무용·공장창고, 세대수 곱셈 불필요).
    private record RawAgeEstimate(int adjustmentYears, BigDecimal targetArea, PriceEstimate unitEstimate, BigDecimal householdFactor) {
    }

    // 아파트/연립다세대 — 세대당 추정시세(준공연도 보정 후 재조회)만 구하고, 세대수 곱셈은 단계 통일
    // 이후(computeAgeAdjustedPrices)에 적용한다 — 통일로 값이 바뀔 수 있어 여기서 미리 곱하면 안 된다.
    private RawAgeEstimate rawByHouseholdGrowth(BuildingEntity building, PropertyType type, RemodelingBasisResponse basis,
                                                  Integer actualBuildYear, int adjustmentYears) {
        Integer additionalHouseholds = basis.estimatedAdditionalHouseholds();
        Integer currentHouseholds = building.getHhCnt();
        if (additionalHouseholds == null || currentHouseholds == null) {
            return null;
        }
        BigDecimal unitArea = PropertyTypeClassifier.displayArea(type, building.getGfa(), building.getHhCnt());
        if (unitArea == null || unitArea.signum() <= 0) {
            return null;
        }
        Integer adjustedBuildYear = actualBuildYear == null ? null : actualBuildYear + adjustmentYears;
        PriceEstimate unitEstimate = estimatePriceForArea(building, type, unitArea, adjustedBuildYear, null, null, null, false);
        BigDecimal householdFactor = BigDecimal.valueOf(currentHouseholds + additionalHouseholds);
        return new RawAgeEstimate(adjustmentYears, unitArea, unitEstimate, householdFactor);
    }

    // 단독다가구/상업업무용/공장창고 — 증축 후 면적 기준, buildYear만 보정.
    private RawAgeEstimate rawByAreaGrowth(BuildingEntity building, PropertyType type, RemodelingBasisResponse basis,
                                             Integer actualBuildYear, int adjustmentYears) {
        BigDecimal additionalBuildableAreaSqm = basis.additionalBuildableAreaSqm();
        if (additionalBuildableAreaSqm == null) {
            return null;
        }
        BigDecimal currentArea = PropertyTypeClassifier.displayArea(type, building.getGfa(), building.getHhCnt());
        if (currentArea == null || currentArea.signum() <= 0) {
            return null;
        }
        BigDecimal postRemodelArea = currentArea.add(additionalBuildableAreaSqm);
        Integer adjustedBuildYear = actualBuildYear == null ? null : actualBuildYear + adjustmentYears;
        PriceEstimate result = estimatePriceForArea(building, type, postRemodelArea, adjustedBuildYear, null, null, null, false);
        return new RawAgeEstimate(adjustmentYears, postRemodelArea, result, null);
    }

    private static EstimatedPriceResponse applyHouseholdFactor(EstimatedPriceResponse unit, BigDecimal factor) {
        if (unit.confidenceLevel() == ConfidenceLevel.UNAVAILABLE) {
            return unit;
        }
        BigDecimal projectedValue = unit.value().multiply(factor);
        return new EstimatedPriceResponse(projectedValue, unit.confidenceLevel(), unit.comparableCount(), unit.comparableTrades(),
                scaleOrNull(unit.conservativeValue(), factor), scaleOrNull(unit.optimisticValue(), factor));
    }

    private static int stageForConfidence(ConfidenceLevel level) {
        return switch (level) {
            case SAME_DONG -> MATCH_STAGE_SAME_DONG;
            case SAME_GU -> MATCH_STAGE_SAME_GU;
            case WIDENED_RANGE -> MATCH_STAGE_WIDENED;
            case DONG_TYPE_AVERAGE -> MATCH_STAGE_DONG_TYPE_AVERAGE;
            case GU_TYPE_AVERAGE, UNAVAILABLE -> MATCH_STAGE_GU_TYPE_AVERAGE;
        };
    }

    // 통일된 완화 단계로 직접 재조회한다 — estimatePriceForArea의 캐스케이드(좁은 단계부터 순서대로
    // 시도)를 건너뛰고 지정된 단계 하나만 바로 조회한다(2026-08-24, 4행 완화 단계 통일 전용).
    private PriceEstimate estimatePriceForAreaAtStage(BuildingEntity building, PropertyType type, BigDecimal targetArea,
                                                        Integer buildYear, int matchStage) {
        String sggNm = building.getSggCdNm();
        String bjdongNm = building.getStdgCdNm();
        BigDecimal areaMin = null;
        BigDecimal areaMax = null;
        Integer yearMin = null;
        Integer yearMax = null;
        String bjdongForStage = null;
        if (matchStage == MATCH_STAGE_SAME_DONG || matchStage == MATCH_STAGE_SAME_GU) {
            areaMin = rangeMin(targetArea, STAGE_AREA_RATIO);
            areaMax = rangeMax(targetArea, STAGE_AREA_RATIO);
            yearMin = rangeMin(buildYear, STAGE_BUILD_YEAR_RANGE);
            yearMax = rangeMax(buildYear, STAGE_BUILD_YEAR_RANGE);
            if (matchStage == MATCH_STAGE_SAME_DONG) {
                bjdongForStage = bjdongNm;
            }
        } else if (matchStage == MATCH_STAGE_WIDENED) {
            areaMin = rangeMin(targetArea, WIDENED_AREA_RATIO);
            areaMax = rangeMax(targetArea, WIDENED_AREA_RATIO);
            yearMin = rangeMin(buildYear, WIDENED_BUILD_YEAR_RANGE);
            yearMax = rangeMax(buildYear, WIDENED_BUILD_YEAR_RANGE);
        } else if (matchStage == MATCH_STAGE_DONG_TYPE_AVERAGE) {
            bjdongForStage = bjdongNm;
        }
        ComparableTradeSearchResult result = fetchStageComparable(null, null, matchStage, type.label(), sggNm,
                bjdongForStage, areaMin, areaMax, yearMin, yearMax);
        if (result.stats().comparableCount() == 0) {
            return new PriceEstimate(EstimatedPriceResponse.unavailable(), null);
        }
        return new PriceEstimate(toEstimatedPrice(result, targetArea, confidenceForStage(matchStage), matchStage), null);
    }

    private static ConfidenceLevel confidenceForStage(int matchStage) {
        return switch (matchStage) {
            case MATCH_STAGE_SAME_DONG -> ConfidenceLevel.SAME_DONG;
            case MATCH_STAGE_SAME_GU -> ConfidenceLevel.SAME_GU;
            case MATCH_STAGE_WIDENED -> ConfidenceLevel.WIDENED_RANGE;
            case MATCH_STAGE_DONG_TYPE_AVERAGE -> ConfidenceLevel.DONG_TYPE_AVERAGE;
            default -> ConfidenceLevel.GU_TYPE_AVERAGE;
        };
    }

    // tradeStatsIndex가 있으면(배치) 메모리 조회로, 없으면(라이브 단건 조회) DB 조회로 완화 단계별
    // 통계를 가져온다 — 3단계 완화 로직·임계값(MIN_COMPARABLE_COUNT)은 완전히 동일하게 공유해서
    // 두 경로의 결과가 갈리지 않게 한다.
    // matchStage(§3.5): 0=법정동/1=구/2=범위 확대/3=법정동×용도 평당가/4=구×용도 평당가 — F-10
    // "유사 사례"(§2.9)가 신뢰도 배지와 같은 색상 규칙으로 표시할 예정이라 ConfidenceLevel과 항상
    // 1:1로 맞춘다. 3/4단계(2026-08-08 추가)는 사용자 제안 "6단계 추정 폴백" 중 3차·5차 — 면적·연식
    // 유사성은 더 이상 안 보고 "그 법정동/구의 같은 유형 실거래가 전체" 평균만 본다. 임의 비율을
    // 지어내는 게 아니라 여전히 실제 관측된 거래에서만 뽑는다는 원칙은 유지(`DOMAIN.md` §4).
    private static final int MATCH_STAGE_SAME_DONG = 0;
    private static final int MATCH_STAGE_SAME_GU = 1;
    private static final int MATCH_STAGE_WIDENED = 2;
    private static final int MATCH_STAGE_DONG_TYPE_AVERAGE = 3;
    private static final int MATCH_STAGE_GU_TYPE_AVERAGE = 4;

    // estimatePriceForArea()의 반환값 — price(기존 §3.6/§3.7)에 §8.17 pricePosition을 함께 묶는다.
    // needsPricePosition=false로 부르면(리모델링 후 예상시세용) pricePosition은 항상 null — 이 지표는
    // "현재가" 전용이라 재사용하지 않는다.
    private record PriceEstimate(EstimatedPriceResponse price, PricePositionResponse pricePosition) {
    }

    // trendCollector가 null이 아니면 0/1단계에서 필터링한 후보를 §3.8 "시세 추이"에도 같이 써서(아래
    // fetchStageComparable) 별도 스캔을 없앤다 — estimatePostRemodelPriceByAreaGrowth(증축 후 면적
    // 기준, 다른 조건)는 트렌드가 필요 없어 null을 넘긴다.
    private PriceEstimate estimatePriceForArea(BuildingEntity building, PropertyType type,
                                                BigDecimal targetArea, Integer buildYear, TradeStatsIndex tradeStatsIndex,
                                                TrendCollector trendCollector, RecentTradeResponse recentTrade,
                                                boolean needsPricePosition) {
        return estimatePriceForArea(building, type, targetArea, buildYear, tradeStatsIndex, trendCollector, recentTrade,
                needsPricePosition, null);
    }

    // overridePricePerSqm(FEATURE_10_MARKET.md CASE2, 2026-08-27 추가) — pricePosition의 순위 기준값을
    // recentTrade 대신 이 값으로 강제한다(실측 매입가 기준). null이면 기존 동작(recentTrade→중앙값 폴백)
    // 그대로. 모집단(p25/median/p75)을 정하는 캐스케이드 자체는 건드리지 않는다 — 04 문서 확정대로
    // "모집단은 estimatedPrice가 resolve된 단계와 완전히 동일"을 유지해야 하기 때문.
    private PriceEstimate estimatePriceForArea(BuildingEntity building, PropertyType type,
                                                BigDecimal targetArea, Integer buildYear, TradeStatsIndex tradeStatsIndex,
                                                TrendCollector trendCollector, RecentTradeResponse recentTrade,
                                                boolean needsPricePosition, BigDecimal overridePricePerSqm) {
        String sggNm = building.getSggCdNm();
        String bjdongNm = building.getStdgCdNm();
        // p25Total/medianTotal/p75Total(§8.17 확장, 2026-08-27) 환산 기준 — 세대기반 유형(아파트·
        // 연립다세대)은 targetArea(세대당면적) × 세대수, 그 외는 targetArea(건물 전체 면적) 그대로.
        // F-19 currentValue(매입가, 항상 "건물 전체 총액")와 같은 스케일을 맞춘다. 세대기반인데 세대수를
        // 모르면(§2.3-f 세대수 결측) null — 그 경우 프론트는 ㎡당가로 폴백한다.
        boolean householdBased = type == PropertyType.APARTMENT || type == PropertyType.ROW_HOUSE;
        BigDecimal areaBase = householdBased
                ? (building.getHhCnt() != null ? targetArea.multiply(BigDecimal.valueOf(building.getHhCnt())) : null)
                : targetArea;

        BigDecimal dongAreaMin = rangeMin(targetArea, STAGE_AREA_RATIO);
        BigDecimal dongAreaMax = rangeMax(targetArea, STAGE_AREA_RATIO);
        Integer dongYearMin = rangeMin(buildYear, STAGE_BUILD_YEAR_RANGE);
        Integer dongYearMax = rangeMax(buildYear, STAGE_BUILD_YEAR_RANGE);
        ComparableTradeSearchResult sameDong = fetchStageComparable(tradeStatsIndex, trendCollector, MATCH_STAGE_SAME_DONG,
                type.label(), sggNm, bjdongNm, dongAreaMin, dongAreaMax, dongYearMin, dongYearMax);
        if (sameDong.stats().comparableCount() >= MIN_COMPARABLE_COUNT) {
            EstimatedPriceResponse price = toEstimatedPrice(sameDong, targetArea, ConfidenceLevel.SAME_DONG, MATCH_STAGE_SAME_DONG);
            PricePositionResponse position = needsPricePosition ? pricePosition(sameDong.stats(), tradeStatsIndex,
                    type.label(), sggNm, bjdongNm, dongAreaMin, dongAreaMax, dongYearMin, dongYearMax, recentTrade, targetArea,
                    overridePricePerSqm, areaBase) : null;
            return new PriceEstimate(price, position);
        }

        BigDecimal guAreaMin = rangeMin(targetArea, STAGE_AREA_RATIO);
        BigDecimal guAreaMax = rangeMax(targetArea, STAGE_AREA_RATIO);
        Integer guYearMin = rangeMin(buildYear, STAGE_BUILD_YEAR_RANGE);
        Integer guYearMax = rangeMax(buildYear, STAGE_BUILD_YEAR_RANGE);
        ComparableTradeSearchResult sameGu = fetchStageComparable(tradeStatsIndex, trendCollector, MATCH_STAGE_SAME_GU,
                type.label(), sggNm, null, guAreaMin, guAreaMax, guYearMin, guYearMax);
        if (sameGu.stats().comparableCount() >= MIN_COMPARABLE_COUNT) {
            EstimatedPriceResponse price = toEstimatedPrice(sameGu, targetArea, ConfidenceLevel.SAME_GU, MATCH_STAGE_SAME_GU);
            PricePositionResponse position = needsPricePosition ? pricePosition(sameGu.stats(), tradeStatsIndex,
                    type.label(), sggNm, null, guAreaMin, guAreaMax, guYearMin, guYearMax, recentTrade, targetArea,
                    overridePricePerSqm, areaBase) : null;
            return new PriceEstimate(price, position);
        }

        // 2단계(범위 확대)는 트렌드가 안 쓰는 단계라 trendCollector를 넘기지 않는다(§3.8).
        BigDecimal widenedAreaMin = rangeMin(targetArea, WIDENED_AREA_RATIO);
        BigDecimal widenedAreaMax = rangeMax(targetArea, WIDENED_AREA_RATIO);
        Integer widenedYearMin = rangeMin(buildYear, WIDENED_BUILD_YEAR_RANGE);
        Integer widenedYearMax = rangeMax(buildYear, WIDENED_BUILD_YEAR_RANGE);
        ComparableTradeSearchResult widened = fetchStageComparable(tradeStatsIndex, null, MATCH_STAGE_WIDENED,
                type.label(), sggNm, null, widenedAreaMin, widenedAreaMax, widenedYearMin, widenedYearMax);
        if (widened.stats().comparableCount() >= MIN_COMPARABLE_COUNT) {
            EstimatedPriceResponse price = toEstimatedPrice(widened, targetArea, ConfidenceLevel.WIDENED_RANGE, MATCH_STAGE_WIDENED);
            PricePositionResponse position = needsPricePosition ? pricePosition(widened.stats(), tradeStatsIndex,
                    type.label(), sggNm, null, widenedAreaMin, widenedAreaMax, widenedYearMin, widenedYearMax, recentTrade, targetArea,
                    overridePricePerSqm, areaBase) : null;
            return new PriceEstimate(price, position);
        }

        // 3단계: 법정동 × 유형 평당가 — 면적·연식 조건 없이 그 법정동의 같은 유형 실거래가 전체.
        ComparableTradeSearchResult dongTypeAverage = fetchStageComparable(tradeStatsIndex, null, MATCH_STAGE_DONG_TYPE_AVERAGE,
                type.label(), sggNm, bjdongNm, null, null, null, null);
        if (dongTypeAverage.stats().comparableCount() >= MIN_COMPARABLE_COUNT) {
            EstimatedPriceResponse price = toEstimatedPrice(dongTypeAverage, targetArea, ConfidenceLevel.DONG_TYPE_AVERAGE, MATCH_STAGE_DONG_TYPE_AVERAGE);
            PricePositionResponse position = needsPricePosition ? pricePosition(dongTypeAverage.stats(), tradeStatsIndex,
                    type.label(), sggNm, bjdongNm, null, null, null, null, recentTrade, targetArea,
                    overridePricePerSqm, areaBase) : null;
            return new PriceEstimate(price, position);
        }

        // 4단계: 구 × 유형 평당가 — 3단계와 동일하되 범위만 구 전체로 확대.
        ComparableTradeSearchResult guTypeAverage = fetchStageComparable(tradeStatsIndex, null, MATCH_STAGE_GU_TYPE_AVERAGE,
                type.label(), sggNm, null, null, null, null, null);
        if (guTypeAverage.stats().comparableCount() >= MIN_COMPARABLE_COUNT) {
            EstimatedPriceResponse price = toEstimatedPrice(guTypeAverage, targetArea, ConfidenceLevel.GU_TYPE_AVERAGE, MATCH_STAGE_GU_TYPE_AVERAGE);
            PricePositionResponse position = needsPricePosition ? pricePosition(guTypeAverage.stats(), tradeStatsIndex,
                    type.label(), sggNm, null, null, null, null, null, recentTrade, targetArea,
                    overridePricePerSqm, areaBase) : null;
            return new PriceEstimate(price, position);
        }

        return new PriceEstimate(EstimatedPriceResponse.unavailable(), null);
    }

    // §8.17 "시장 내 가격 위치" — stats(p25/median/p75는 방금 이긴 단계에서 이미 계산됨)에 thisPropertyPercentile만
    // 추가로 구한다. 이 매물의 ㎡당가는 overridePricePerSqm(FEATURE_10_MARKET.md CASE2, F-19 실측 매입가)이
    // 있으면 그대로 쓰고, 없으면(공공데이터 기준, 기존 동작) §8.16과 같은 판정(RepresentativePriceCalculator)
    // 으로 recentTrade가 지분거래가 아니면 그 실거래 ㎡당가, 아니면(또는 recentTrade 자체가 없으면) 중앙값
    // 그대로 — 후자는 정의상 정확히 50 percentile이 나온다.
    private PricePositionResponse pricePosition(ComparableTradeStatsReadModel stats, TradeStatsIndex tradeStatsIndex,
                                                  String propertyType, String sggNm, String bjdongNm,
                                                  BigDecimal areaMin, BigDecimal areaMax,
                                                  Integer buildYearMin, Integer buildYearMax,
                                                  RecentTradeResponse recentTrade, BigDecimal targetArea,
                                                  BigDecimal overridePricePerSqm, BigDecimal areaBase) {
        BigDecimal thisPricePerSqm = overridePricePerSqm != null ? overridePricePerSqm
                : RepresentativePriceCalculator.representativePricePerSqm(recentTrade, stats.medianPricePerSqm(), targetArea);
        // estimateFallback — overridePricePerSqm(실측)이 없고 recentTrade도 대표성이 없어(또는 아예
        // 없어) 중앙값으로 대신 채운 경우. 이 판정은 representativePricePerSqm 내부와 동일해야 하므로
        // RepresentativePriceCalculator.isRepresentative()를 그대로 재사용한다(로직 이중화 방지).
        boolean estimateFallback = overridePricePerSqm == null
                && !(recentTrade != null && RepresentativePriceCalculator.isRepresentative(recentTrade, targetArea));
        BigDecimal rank;
        if (tradeStatsIndex != null) {
            rank = tradeStatsIndex.percentRank(propertyType, sggNm, bjdongNm, areaMin, areaMax, buildYearMin, buildYearMax, thisPricePerSqm);
        } else {
            LocalDate recencyCutoff = LocalDate.now().minusMonths(RECENCY_WINDOW_MONTHS);
            PricePositionRankCondition condition = new PricePositionRankCondition(propertyType, sggNm, bjdongNm,
                    areaMin, areaMax, buildYearMin, buildYearMax, recencyCutoff, thisPricePerSqm);
            rank = marketMapper.findComparableTradeRank(condition);
        }
        BigDecimal p25Total = areaBase != null ? stats.p25PricePerSqm().multiply(areaBase) : null;
        BigDecimal medianTotal = areaBase != null ? stats.medianPricePerSqm().multiply(areaBase) : null;
        BigDecimal p75Total = areaBase != null ? stats.p75PricePerSqm().multiply(areaBase) : null;
        return new PricePositionResponse(stats.p25PricePerSqm(), stats.medianPricePerSqm(), stats.p75PricePerSqm(), rank,
                p25Total, medianTotal, p75Total, estimateFallback);
    }

    // §3.8 "시세 추이" — estimatePriceForArea()의 0/1단계 완화 판정에 쓰던 TrendCollector에 값을 담아둔다
    // (offer는 이미 확정된 트렌드가 있거나 2단계를 넘으면 무시). 배치(tradeStatsIndex!=null)는
    // TradeStatsIndex.stageResult()로 필터링을 한 번만 해서 통계+월별추이를 동시에 얻는다(2026-08-08
    // 성능 개선). 라이브 단건 조회는 건물 1개짜리라 쿼리 2번이 문제 아니라서 기존처럼 따로 부른다.
    private ComparableTradeSearchResult fetchStageComparable(TradeStatsIndex tradeStatsIndex, TrendCollector trendCollector,
                                                               int matchStage, String propertyType, String sggNm,
                                                               String bjdongNm, BigDecimal areaMin, BigDecimal areaMax,
                                                               Integer buildYearMin, Integer buildYearMax) {
        if (tradeStatsIndex != null) {
            TradeStatsIndex.StageResult stage = tradeStatsIndex.stageResult(
                    propertyType, sggNm, bjdongNm, areaMin, areaMax, buildYearMin, buildYearMax);
            if (trendCollector != null) {
                trendCollector.offer(matchStage, stage.monthlyTrend());
            }
            return stage.comparable();
        }
        LocalDate recencyCutoff = LocalDate.now().minusMonths(RECENCY_WINDOW_MONTHS);
        MarketComparableCondition condition = new MarketComparableCondition(
                sggNm, bjdongNm, propertyType, areaMin, areaMax, buildYearMin, buildYearMax, recencyCutoff);
        ComparableTradeStatsReadModel stats = marketMapper.findComparableTradeStats(condition);
        List<ComparableTradeSampleReadModel> samples =
                stats.comparableCount() > 0 ? marketMapper.findComparableTradeSamples(condition) : List.of();
        if (trendCollector != null) {
            trendCollector.offer(matchStage, marketMapper.findMonthlyPriceTrend(condition));
        }
        return new ComparableTradeSearchResult(stats, samples);
    }

    // 0/1단계 중 먼저 MIN_TREND_MONTHS를 채우는 단계를 채택하고, 그 이후 호출은 전부 무시(2단계는 애초에
    // 호출 자체가 안 됨 — 위 estimatePriceForArea가 widened 단계엔 trendCollector를 안 넘김).
    private static final class TrendCollector {
        private PriceTrendResponse trend;
        private boolean resolved;

        void offer(int matchStage, List<PriceTrendPointReadModel> points) {
            if (resolved || points.size() < MIN_TREND_MONTHS) {
                return;
            }
            resolved = true;
            List<PriceTrendPointResponse> responsePoints = points.stream()
                    .map(r -> new PriceTrendPointResponse(r.month(), r.medianPricePerSqm(), (int) r.tradeCount()))
                    .toList();
            trend = new PriceTrendResponse(matchStage, responsePoints);
        }
    }

    private static EstimatedPriceResponse toEstimatedPrice(ComparableTradeSearchResult result, BigDecimal targetArea,
                                                             ConfidenceLevel confidenceLevel, int matchStage) {
        BigDecimal value = result.stats().medianPricePerSqm().multiply(targetArea).setScale(0, RoundingMode.HALF_UP);
        List<ComparableTradeResponse> comparableTrades = result.samples().stream()
                .map(s -> new ComparableTradeResponse(s.bjdongNm(), s.areaSqm(), s.price10kWon(), s.contractDate(), matchStage))
                .toList();
        BigDecimal[] scenarioRange = scenarioRange(result.samples(), targetArea);
        return new EstimatedPriceResponse(value, confidenceLevel, result.stats().comparableCount(), comparableTrades,
                scenarioRange[0], scenarioRange[1]);
    }

    // §3.9 "미래가치" 3-way 시나리오 — comparableTrades(최대 5건, 계약일 최신순)의 ㎡당가격 최저/최고를
    // 대상 면적에 곱해 보수적/낙관적 값을 만든다. 임의의 ±% 가정 없이 실제 관측된 거래에서만 뽑는다
    // (`DOMAIN.md` §4 "추측 기반 판단 금지"). 표본 2건 미만이면 최저=최고=중앙값이라 의미가 없어 null.
    private static BigDecimal[] scenarioRange(List<ComparableTradeSampleReadModel> samples, BigDecimal targetArea) {
        if (samples.size() < 2) {
            return new BigDecimal[]{null, null};
        }
        BigDecimal minRatio = null;
        BigDecimal maxRatio = null;
        for (ComparableTradeSampleReadModel sample : samples) {
            BigDecimal ratio = sample.price10kWon().divide(sample.areaSqm(), 10, RoundingMode.HALF_UP);
            if (minRatio == null || ratio.compareTo(minRatio) < 0) {
                minRatio = ratio;
            }
            if (maxRatio == null || ratio.compareTo(maxRatio) > 0) {
                maxRatio = ratio;
            }
        }
        BigDecimal conservativeValue = minRatio.multiply(targetArea).setScale(0, RoundingMode.HALF_UP);
        BigDecimal optimisticValue = maxRatio.multiply(targetArea).setScale(0, RoundingMode.HALF_UP);
        return new BigDecimal[]{conservativeValue, optimisticValue};
    }

    private static BigDecimal rangeMin(BigDecimal target, BigDecimal ratio) {
        return target.multiply(BigDecimal.ONE.subtract(ratio));
    }

    private static BigDecimal rangeMax(BigDecimal target, BigDecimal ratio) {
        return target.multiply(BigDecimal.ONE.add(ratio));
    }

    private static Integer rangeMin(Integer target, int range) {
        return target == null ? null : target - range;
    }

    private static Integer rangeMax(Integer target, int range) {
        return target == null ? null : target + range;
    }

    // §3.6 "공시가격" — 세대별(구분소유)로 여러 행이 매칭될 수 있어 최신 연/월 행만 평균한다.
    // apartment_price(공동주택)는 아파트/연립주택/다세대주택만 커버하고, 그 외(단독주택·다가구주택)는
    // detached_house_price(F-16 §5.1)를 본다 — 건물 하나는 둘 중 한쪽에만 매칭되므로 apartment_price가
    // 없을 때만 detached_house_price로 폴백한다. 두 테이블 모두 price가 원 단위로 적재돼 있어(F-16),
    // recentTrade/estimatedPrice(만원 단위)와 맞추기 위해 10000으로 나눈다 — 단위를 안 맞추면 프론트가
    // 자릿수를 착각하기 쉽다.
    private BigDecimal latestOfficialPrice(String buildingId) {
        return latestOfficialPriceFromRows(apartmentPriceRepository.findByBuildingId(buildingId),
                detachedHousePriceRepository.findByBuildingId(buildingId));
    }

    private static BigDecimal latestOfficialPriceFromRows(List<ApartmentPriceEntity> apartmentRows,
                                                            List<DetachedHousePriceEntity> detachedRows) {
        BigDecimal wonPrice = averageAtLatestPeriod(apartmentRows, ApartmentPriceEntity::getBaseYear,
                ApartmentPriceEntity::getBaseMonth, ApartmentPriceEntity::getPrice);
        if (wonPrice == null) {
            wonPrice = averageAtLatestPeriod(detachedRows, DetachedHousePriceEntity::getBaseYear,
                    DetachedHousePriceEntity::getBaseMonth, DetachedHousePriceEntity::getPrice);
        }
        return wonPrice == null ? null : wonPrice.divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP);
    }

    // §3.6 "토지당 가격" — 개별공시지가는 성격 자체가 "원/㎡"(총액이 아니라 단가)라 apartment_price처럼
    // 만원 단위로 환산하지 않고 원 단위 그대로 노출한다(§2.1 "토지당 가격" 명칭 자체가 단가라는 뜻).
    private BigDecimal latestLandPrice(String buildingId) {
        return latestLandPriceFromRows(landPriceRepository.findByBuildingId(buildingId));
    }

    private static BigDecimal latestLandPriceFromRows(List<LandPriceEntity> rows) {
        return averageAtLatestPeriod(rows, LandPriceEntity::getBaseYear, LandPriceEntity::getBaseMonth,
                LandPriceEntity::getPrice);
    }

    private static <T> BigDecimal averageAtLatestPeriod(List<T> rows, java.util.function.Function<T, Integer> yearOf,
                                                         java.util.function.Function<T, Integer> monthOf,
                                                         java.util.function.Function<T, BigDecimal> priceOf) {
        List<T> withYear = rows.stream().filter(r -> yearOf.apply(r) != null).toList();
        if (withYear.isEmpty()) {
            return null;
        }
        int latestYear = withYear.stream().mapToInt(yearOf::apply).max().orElseThrow();
        List<T> atLatestYear = withYear.stream().filter(r -> yearOf.apply(r) == latestYear).toList();
        int latestMonth = atLatestYear.stream()
                .map(monthOf).filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).max().orElse(-1);
        List<BigDecimal> prices = atLatestYear.stream()
                .filter(r -> latestMonth < 0 || java.util.Objects.equals(monthOf.apply(r), latestMonth))
                .map(priceOf)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (prices.isEmpty()) {
            return null;
        }
        BigDecimal sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(prices.size()), 0, RoundingMode.HALF_UP);
    }
}
