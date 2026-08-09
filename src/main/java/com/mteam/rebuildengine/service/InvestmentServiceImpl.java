package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.model.entity.InvestmentResultEntity;
import com.mteam.rebuildengine.model.response.BuildingAnalysisResponse;
import com.mteam.rebuildengine.model.response.ConfidenceLevel;
import com.mteam.rebuildengine.model.response.CostEstimationResponse;
import com.mteam.rebuildengine.model.response.CostEstimationStatus;
import com.mteam.rebuildengine.model.response.InvestmentEvaluationResponse;
import com.mteam.rebuildengine.model.response.InvestmentSnapshot;
import com.mteam.rebuildengine.model.response.MarketAnalysisResponse;
import com.mteam.rebuildengine.model.response.RemodelingResultResponse;
import com.mteam.rebuildengine.model.response.RemodelingVerdict;
import com.mteam.rebuildengine.repository.BuildingRepository;
import com.mteam.rebuildengine.repository.InvestmentResultRepository;
import com.mteam.rebuildengine.utils.InvestmentEvaluationStage;
import com.mteam.rebuildengine.utils.InvestmentGrade;
import com.mteam.rebuildengine.utils.PropertyType;
import com.mteam.rebuildengine.utils.PropertyTypeClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

// FEATURE_09_INVESTMENT.md §3.2 V1 등급 산정 공식 — F-06(게이트+점수)·F-07(공사비 범위)·F-08(시세+
// §3.7 리모델링 후 예상 시세)을 결합한 3단계 판정(게이트 → ROI산출가능여부 → 등급매핑). computeSnapshot()은
// 이 3개 원본 응답까지 함께 반환 — InvestmentAnalysisBatchService(§3.4)가 investment_result에 그대로
// 저장한다. `investment_result` 스파이크(§3.1, 랜덤/시세프리미엄)는 이 공식으로 대체 완료.
@Service
@RequiredArgsConstructor
public class InvestmentServiceImpl implements InvestmentService {

    // ROI 기반(③ 단계) 등급 경계 — FEATURE_09_INVESTMENT.md §3.3(2026-08-1x, 6종→4종+NA 축소) 인접
    // 등급 통합본. 상한 클램프는 하지 않는다 — 실제 계산값이라 20%를 넘어도 그대로 A 처리.
    private static final BigDecimal ROI_A = BigDecimal.valueOf(20);
    private static final BigDecimal ROI_B = BigDecimal.valueOf(10);
    private static final BigDecimal ROI_C = BigDecimal.valueOf(5);

    private final BuildingRepository buildingRepository;
    private final RemodelingService remodelingService;
    private final CostService costService;
    private final MarketService marketService;
    private final InvestmentResultRepository investmentResultRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<InvestmentEvaluationResponse> evaluate(String buildingId) {
        return computeSnapshot(buildingId).map(InvestmentSnapshot::investment);
    }

    @Override
    public Optional<InvestmentSnapshot> computeSnapshot(String buildingId) {
        return buildingRepository.findByBdrgSnAndIsAncillaryFalseAndIsOutOfScopeFalseAndIsDeletedFalse(buildingId).flatMap(this::computeSnapshot);
    }

    // 라이브 단건 조회(computeSnapshot(String)) 전용 — DB에서 매번 조회한다.
    private Optional<InvestmentSnapshot> computeSnapshot(BuildingEntity building) {
        return remodelingService.getRemodelingResult(building.getBdrgSn()).map(remodeling -> {
            // remodeling을 그대로 넘겨 CostService/MarketService가 F-06을 다시 계산하지 않게 한다
            // (중복 계산 제거, 2026-08-08 배치 성능 실측 중 발견).
            CostEstimationResponse cost = costService.getCostEstimation(building, remodeling);
            MarketAnalysisResponse market = marketService.getMarketAnalysis(building, remodeling);
            InvestmentEvaluationResponse investment = evaluateGrade(building, remodeling, cost, market);
            return new InvestmentSnapshot(remodeling, cost, market, investment);
        });
    }

    // F-09 V1 배치(InvestmentAnalysisBatchService) — 페이지 단위 벌크 조회(bundle)·배치 시작 시 1회
    // 로드한 유사거래 인덱스(tradeStatsIndex)를 그대로 쓴다. evaluateGrade()는 라이브 단건 조회 경로와
    // 완전히 동일한 메서드를 공유 — 계산 로직 이중화 없음.
    @Override
    public InvestmentSnapshot computeSnapshot(BuildingEntity building, BuildingDataBundle bundle,
                                                TradeStatsIndex tradeStatsIndex) {
        RemodelingResultResponse remodeling = remodelingService.getRemodelingResult(building, bundle);
        CostEstimationResponse cost = costService.getCostEstimation(building, remodeling);
        MarketAnalysisResponse market = marketService.getMarketAnalysis(building, remodeling, bundle, tradeStatsIndex);
        InvestmentEvaluationResponse investment = evaluateGrade(building, remodeling, cost, market);
        return new InvestmentSnapshot(remodeling, cost, market, investment);
    }

    private InvestmentEvaluationResponse evaluateGrade(BuildingEntity building, RemodelingResultResponse remodeling,
                                                        CostEstimationResponse cost, MarketAnalysisResponse market) {
        // ① 게이트 — F-06 "불가" 판정은 D 고정, ROI 계산 스킵.
        if (remodeling.verdict() == RemodelingVerdict.NOT_POSSIBLE) {
            return new InvestmentEvaluationResponse(InvestmentGrade.D, null, InvestmentEvaluationStage.GATE);
        }

        Optional<PropertyType> type = PropertyTypeClassifier.classify(building.getMnUsgCdNm(), building.getGrndNofl());
        boolean householdBased = type.isPresent()
                && (type.get() == PropertyType.APARTMENT || type.get() == PropertyType.ROW_HOUSE);

        // §3.2 "4대 입력값" — 하나라도 없으면 ② 점수 단독 폴백(0인 것과 없는 것은 다르다, 0은 정상 케이스).
        boolean currentPriceAvailable = householdBased
                ? market.estimatedPrice().confidenceLevel() != ConfidenceLevel.UNAVAILABLE
                : market.recentTrade() != null || market.estimatedPrice().confidenceLevel() != ConfidenceLevel.UNAVAILABLE;
        boolean costAvailable = cost.status() == CostEstimationStatus.AVAILABLE;
        boolean growthAvailable = householdBased
                ? remodeling.basis().estimatedAdditionalHouseholds() != null
                : remodeling.basis().additionalBuildableAreaSqm() != null;
        boolean postRemodelAvailable = market.postRemodelEstimatedPrice() != null;

        // ② 점수 폴백 — grade="NA"(정보부족), roi=null(2026-08-09 변경, FEATURE_09_INVESTMENT.md §3.3).
        // 예전엔 F-06 achievementRate(건물나이÷허용연한×100, 상한 없음)를 grade로 대체했으나, 정작
        // 대지면적·시세 등 핵심 정보가 없어서 이 단계로 떨어진 건물일수록 오래된 건물이 많고 오래될수록
        // achievementRate가 커져 역설적으로 A+로 몰리는 문제가 확인됐다 — "정보가 없다"는 사실이 등급을
        // 깎기는커녕 밀어올리는 구조라 A~D 등급 산출 자체를 포기하고 NA로 명확히 구분한다.
        if (!currentPriceAvailable || !costAvailable || !growthAvailable || !postRemodelAvailable) {
            return new InvestmentEvaluationResponse(InvestmentGrade.NA, null, InvestmentEvaluationStage.SCORE_FALLBACK);
        }

        // ③ 정상 산출 — 세대/비세대 유형별로 "현재가"만 다르고 나머지 식은 동일
        // (FEATURE_08_MARKET.md §3.7 "수익분석", FEATURE.md §8.9 스케일 정정 반영).
        BigDecimal currentValue = householdBased
                ? market.estimatedPrice().value().multiply(BigDecimal.valueOf(building.getHhCnt()))
                : (market.recentTrade() != null ? market.recentTrade().price() : market.estimatedPrice().value());
        BigDecimal projectedValue = market.postRemodelEstimatedPrice().value();

        // F-07 minCost/maxCost는 원 단위(FEATURE_07_COST.md, baseUnitPricePerSqm이 원/㎡)인데
        // currentValue/projectedValue는 F-08 단위 확정대로 만원 단위(FEATURE_08_MARKET.md §3.6)라
        // 그대로 더하면 10000배 스케일이 어긋난다 — 공사비를 만원 단위로 환산해서 맞춘다.
        BigDecimal maxCostIn10kWon = cost.maxCost().divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP);

        // 최대 공사비 적용 시 최소 수익률 — 등급은 이 보수적 값 하나로 매핑(§3.2 "min을 쓰는 이유").
        BigDecimal totalInvestmentMax = currentValue.add(maxCostIn10kWon);
        BigDecimal minRoi = projectedValue.subtract(totalInvestmentMax)
                .multiply(BigDecimal.valueOf(100))
                .divide(totalInvestmentMax, 2, RoundingMode.HALF_UP);

        return new InvestmentEvaluationResponse(gradeFromRoi(minRoi), minRoi, InvestmentEvaluationStage.FULL);
    }

    // §3.4 — investment_result 스냅샷을 읽기만 한다(실시간 재계산 아님, F-05/F-10용).
    @Override
    public Optional<BuildingAnalysisResponse> getStoredAnalysis(String buildingId) {
        return investmentResultRepository.findById(buildingId)
                .filter(entity -> !entity.isDeleted())
                .map(entity -> new BuildingAnalysisResponse(
                        entity.getGrade(),
                        entity.getRoi(),
                        readJson(entity.getRemodelingBasis(), RemodelingResultResponse.class),
                        readJson(entity.getCostBasis(), CostEstimationResponse.class),
                        readJson(entity.getMarketBasis(), MarketAnalysisResponse.class),
                        entity.getUpdatedAt()
                ));
    }

    private <T> T readJson(String json, Class<T> type) {
        return json == null ? null : objectMapper.readValue(json, type);
    }

    private static InvestmentGrade gradeFromRoi(BigDecimal roi) {
        if (roi.compareTo(ROI_A) >= 0) return InvestmentGrade.A;
        if (roi.compareTo(ROI_B) >= 0) return InvestmentGrade.B;
        if (roi.compareTo(ROI_C) >= 0) return InvestmentGrade.C;
        return InvestmentGrade.D;
    }
}
