package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.mapper.MarketComparableCondition;
import com.mteam.rebuildengine.mapper.MarketMapper;
import com.mteam.rebuildengine.model.entity.ApartmentPriceEntity;
import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.model.entity.DetachedHousePriceEntity;
import com.mteam.rebuildengine.model.entity.LandPriceEntity;
import com.mteam.rebuildengine.model.entity.TradeEntity;
import com.mteam.rebuildengine.model.read.ComparableTradeSampleReadModel;
import com.mteam.rebuildengine.model.read.ComparableTradeSearchResult;
import com.mteam.rebuildengine.model.read.ComparableTradeStatsReadModel;
import com.mteam.rebuildengine.model.read.PriceTrendPointReadModel;
import com.mteam.rebuildengine.model.response.ComparableTradeResponse;
import com.mteam.rebuildengine.model.response.ConfidenceLevel;
import com.mteam.rebuildengine.model.response.EstimatedPriceResponse;
import com.mteam.rebuildengine.model.response.MarketAnalysisResponse;
import com.mteam.rebuildengine.model.response.PriceTrendPointResponse;
import com.mteam.rebuildengine.model.response.PriceTrendResponse;
import com.mteam.rebuildengine.model.response.RecentTradeResponse;
import com.mteam.rebuildengine.model.response.RemodelingBasisResponse;
import com.mteam.rebuildengine.model.response.RemodelingResultResponse;
import com.mteam.rebuildengine.model.response.RemodelingVerdict;
import com.mteam.rebuildengine.repository.ApartmentPriceRepository;
import com.mteam.rebuildengine.repository.BuildingRepository;
import com.mteam.rebuildengine.repository.DetachedHousePriceRepository;
import com.mteam.rebuildengine.repository.LandPriceRepository;
import com.mteam.rebuildengine.repository.TradeRepository;
import com.mteam.rebuildengine.utils.PropertyType;
import com.mteam.rebuildengine.utils.PropertyTypeClassifier;
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

    // §3.4-B 유사 거래 기준(면적 ±비율, 연식 ±년) — 상세 설계 시 확정 전까지 backend가 정하는 초기값.
    private static final BigDecimal STAGE_AREA_RATIO = BigDecimal.valueOf(0.20);
    private static final int STAGE_BUILD_YEAR_RANGE = 5;
    private static final BigDecimal WIDENED_AREA_RATIO = BigDecimal.valueOf(0.35);
    private static final int WIDENED_BUILD_YEAR_RANGE = 10;
    // §3.4-B-3 "시점 보정"의 단순화 버전 — 최근 3년 이내 거래만 비교 대상으로 삼는다.
    private static final long RECENCY_WINDOW_MONTHS = 36;
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
        return buildingRepository.findById(buildingId).flatMap(building ->
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
    // 그대로 공유해서 라이브 조회와 결과가 갈리지 않는다.
    @Override
    public MarketAnalysisResponse getMarketAnalysis(BuildingEntity building, RemodelingResultResponse remodeling,
                                                      BuildingDataBundle bundle, TradeStatsIndex tradeStatsIndex) {
        CurrentEstimate current = estimateCurrentPriceAndTrend(building, tradeStatsIndex);
        return new MarketAnalysisResponse(
                toRecentTrade(bundle.recentTrades(building.getBdrgSn())),
                current.price(),
                latestOfficialPriceFromRows(bundle.apartmentPrices(building.getBdrgSn()),
                        bundle.detachedHousePrices(building.getBdrgSn())),
                latestLandPriceFromRows(bundle.landPrices(building.getBdrgSn())),
                estimatePostRemodelPrice(building, remodeling, current.price(), tradeStatsIndex),
                current.trend());
    }

    @Override
    public TradeStatsIndex loadTradeStatsIndex() {
        LocalDate recencyCutoff = LocalDate.now().minusMonths(RECENCY_WINDOW_MONTHS);
        return TradeStatsIndex.load(marketMapper.findAllComparableTradeRows(recencyCutoff));
    }

    private MarketAnalysisResponse buildMarketAnalysis(BuildingEntity building, RemodelingResultResponse remodeling,
                                                         RecentTradeResponse recentTrade, BigDecimal officialPrice,
                                                         BigDecimal landPrice) {
        CurrentEstimate current = estimateCurrentPriceAndTrend(building, null);
        return new MarketAnalysisResponse(recentTrade, current.price(), officialPrice, landPrice,
                estimatePostRemodelPrice(building, remodeling, current.price(), null),
                current.trend());
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
    private record CurrentEstimate(EstimatedPriceResponse price, PriceTrendResponse trend) {
    }

    // tradeStatsIndex가 있으면(배치) 메모리에서, 없으면(라이브 단건 조회) DB에서 계산한다.
    private CurrentEstimate estimateCurrentPriceAndTrend(BuildingEntity building, TradeStatsIndex tradeStatsIndex) {
        Optional<PropertyType> type = PropertyTypeClassifier.classify(building.getMnUsgCdNm(), building.getGrndNofl());
        if (type.isEmpty()) {
            return new CurrentEstimate(EstimatedPriceResponse.unavailable(), null);
        }
        BigDecimal targetArea = PropertyTypeClassifier.displayArea(type.get(), building.getGfa(), building.getHhCnt());
        if (targetArea == null || targetArea.signum() <= 0) {
            return new CurrentEstimate(EstimatedPriceResponse.unavailable(), null);
        }
        TrendCollector trendCollector = new TrendCollector();
        EstimatedPriceResponse price = estimatePriceForArea(building, type.get(), targetArea, tradeStatsIndex, trendCollector);
        return new CurrentEstimate(price, trendCollector.trend);
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
    // 증축 후 면적(현재 면적 + additionalBuildableAreaSqm)으로 바꾼다.
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
        EstimatedPriceResponse result = estimatePriceForArea(building, type, postRemodelArea, tradeStatsIndex, null);
        return result.confidenceLevel() == ConfidenceLevel.UNAVAILABLE ? null : result;
    }

    // tradeStatsIndex가 있으면(배치) 메모리 조회로, 없으면(라이브 단건 조회) DB 조회로 완화 단계별
    // 통계를 가져온다 — 3단계 완화 로직·임계값(MIN_COMPARABLE_COUNT)은 완전히 동일하게 공유해서
    // 두 경로의 결과가 갈리지 않게 한다.
    // matchStage(§3.5): 0=법정동/1=구/2=범위 확대 — F-10 "유사 사례"(§2.9)가 신뢰도 배지와 같은
    // 색상 규칙으로 표시할 예정이라 ConfidenceLevel과 항상 1:1로 맞춘다.
    private static final int MATCH_STAGE_SAME_DONG = 0;
    private static final int MATCH_STAGE_SAME_GU = 1;
    private static final int MATCH_STAGE_WIDENED = 2;

    // trendCollector가 null이 아니면 0/1단계에서 필터링한 후보를 §3.8 "시세 추이"에도 같이 써서(아래
    // fetchStageComparable) 별도 스캔을 없앤다 — estimatePostRemodelPriceByAreaGrowth(증축 후 면적
    // 기준, 다른 조건)는 트렌드가 필요 없어 null을 넘긴다.
    private EstimatedPriceResponse estimatePriceForArea(BuildingEntity building, PropertyType type,
                                                          BigDecimal targetArea, TradeStatsIndex tradeStatsIndex,
                                                          TrendCollector trendCollector) {
        Integer buildYear = building.getUseAprvYmd() == null ? null : building.getUseAprvYmd().getYear();

        ComparableTradeSearchResult sameDong = fetchStageComparable(tradeStatsIndex, trendCollector, MATCH_STAGE_SAME_DONG,
                type.label(), building.getSggCdNm(), building.getStdgCdNm(),
                rangeMin(targetArea, STAGE_AREA_RATIO), rangeMax(targetArea, STAGE_AREA_RATIO),
                rangeMin(buildYear, STAGE_BUILD_YEAR_RANGE), rangeMax(buildYear, STAGE_BUILD_YEAR_RANGE));
        if (sameDong.stats().comparableCount() >= MIN_COMPARABLE_COUNT) {
            return toEstimatedPrice(sameDong, targetArea, ConfidenceLevel.SAME_DONG, MATCH_STAGE_SAME_DONG);
        }

        ComparableTradeSearchResult sameGu = fetchStageComparable(tradeStatsIndex, trendCollector, MATCH_STAGE_SAME_GU,
                type.label(), building.getSggCdNm(), null,
                rangeMin(targetArea, STAGE_AREA_RATIO), rangeMax(targetArea, STAGE_AREA_RATIO),
                rangeMin(buildYear, STAGE_BUILD_YEAR_RANGE), rangeMax(buildYear, STAGE_BUILD_YEAR_RANGE));
        if (sameGu.stats().comparableCount() >= MIN_COMPARABLE_COUNT) {
            return toEstimatedPrice(sameGu, targetArea, ConfidenceLevel.SAME_GU, MATCH_STAGE_SAME_GU);
        }

        // 2단계(범위 확대)는 트렌드가 안 쓰는 단계라 trendCollector를 넘기지 않는다(§3.8).
        ComparableTradeSearchResult widened = fetchStageComparable(tradeStatsIndex, null, MATCH_STAGE_WIDENED,
                type.label(), building.getSggCdNm(), null,
                rangeMin(targetArea, WIDENED_AREA_RATIO), rangeMax(targetArea, WIDENED_AREA_RATIO),
                rangeMin(buildYear, WIDENED_BUILD_YEAR_RANGE), rangeMax(buildYear, WIDENED_BUILD_YEAR_RANGE));
        if (widened.stats().comparableCount() >= MIN_COMPARABLE_COUNT) {
            return toEstimatedPrice(widened, targetArea, ConfidenceLevel.WIDENED_RANGE, MATCH_STAGE_WIDENED);
        }

        return EstimatedPriceResponse.unavailable();
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
