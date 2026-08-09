package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.entity.ApartmentPriceEntity;
import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.model.entity.DetachedHousePriceEntity;
import com.mteam.rebuildengine.model.entity.LandPriceEntity;
import com.mteam.rebuildengine.model.entity.LanduseDistrictEntity;
import com.mteam.rebuildengine.model.entity.LanduseEntity;
import com.mteam.rebuildengine.model.entity.PermitEntity;
import com.mteam.rebuildengine.model.entity.TradeEntity;
import com.mteam.rebuildengine.model.response.InvestmentSnapshot;
import com.mteam.rebuildengine.repository.ApartmentPriceRepository;
import com.mteam.rebuildengine.repository.BuildingRepository;
import com.mteam.rebuildengine.repository.DetachedHousePriceRepository;
import com.mteam.rebuildengine.repository.LandPriceRepository;
import com.mteam.rebuildengine.repository.LanduseDistrictRepository;
import com.mteam.rebuildengine.repository.LanduseRepository;
import com.mteam.rebuildengine.repository.PermitRepository;
import com.mteam.rebuildengine.repository.TradeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

// FEATURE_09_INVESTMENT.md §3.4 — investment_result를 F-09 V1 공식(F-06+F-07+F-08 결합)의 실제
// 계산값으로 채우는 배치. 585,331건을 건물마다 개별 조회하면 너무 느리다(2026-08-08 실측 — 건당
// 15~20개 쿼리, 인덱스 튜닝만으로는 초당 20건대에 그침) — 두 가지로 줄인다: ① building_id 기반
// 참조 테이블(permit/landuse/landuse_district/apartment_price/land_price/trade)을 페이지(1000건)
// 단위 IN 절로 벌크 조회(BuildingDataBundle), ② F-08 유사거래 비교(§3.4-B)는 배치 시작 시 trade를
// 통째로 1회만 읽어 메모리 인덱스(TradeStatsIndex)로 계산 — 건물마다 DB를 다시 묻지 않는다. F-06/
// F-07/F-08 계산 로직 자체는 SQL로 다시 만들지 않고 기존 서비스를 그대로 재사용 — 계산 로직이
// 두 곳(Java/SQL)에 이중으로 생기는 것을 막는다.
@Service
@RequiredArgsConstructor
public class InvestmentAnalysisBatchServiceImpl implements InvestmentAnalysisBatchService {

    private static final Logger logger = LogManager.getLogger(InvestmentAnalysisBatchServiceImpl.class);
    private static final int PAGE_SIZE = 1000;
    // 오래된 로컬 PC(CPU 4코어) 부담을 줄이기 위해 스레드 수를 늘리지 않는다(2026-08-08).
    private static final int THREAD_POOL_SIZE = 8;
    private static final String[] CSV_HEADER =
            {"building_id", "grade", "roi", "remodeling_basis", "cost_basis", "market_basis"};

    private final BuildingRepository buildingRepository;
    private final PermitRepository permitRepository;
    private final LanduseRepository landuseRepository;
    private final LanduseDistrictRepository landuseDistrictRepository;
    private final ApartmentPriceRepository apartmentPriceRepository;
    private final LandPriceRepository landPriceRepository;
    private final DetachedHousePriceRepository detachedHousePriceRepository;
    private final TradeRepository tradeRepository;
    private final InvestmentService investmentService;
    private final MarketService marketService;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    @Value("${data-migration.data-dir}")
    private String dataDir;

    @Override
    public ExportResult exportAnalysisCsv() {
        AtomicInteger total = new AtomicInteger();
        Path outputPath = Path.of(dataDir, "converted", "investment_result_export.csv");
        logger.info("F-08 유사거래 인덱스 로딩 시작(배치 전체에서 1회만)");
        TradeStatsIndex tradeStatsIndex = marketService.loadTradeStatsIndex();
        logger.info("F-08 유사거래 인덱스 로딩 완료");
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(CSV_HEADER).build())) {
            processPages(executor, printer, total, tradeStatsIndex);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            executor.shutdown();
        }
        ExportResult result = new ExportResult(total.get(), 0);
        logger.info("investment_result V1 분석 CSV 출력 완료: {} ({}건)", outputPath, result.total());
        return result;
    }

    // BuildingGisMappingServiceImpl.processPages와 동일한 keyset pagination — OFFSET 페이징은
    // 585K건 배치에서 페이지가 진행될수록 점점 느려지는 문제가 실측됐다(2026-07-27).
    private void processPages(ExecutorService executor, CSVPrinter printer, AtomicInteger total,
                               TradeStatsIndex tradeStatsIndex) {
        for (String lastBdrgSn = ""; lastBdrgSn != null; ) {
            List<BuildingEntity> batch =
                    buildingRepository.findByBdrgSnGreaterThanAndIsAncillaryFalseAndIsOutOfScopeFalseAndIsDeletedFalseOrderByBdrgSnAsc(lastBdrgSn, Pageable.ofSize(PAGE_SIZE));
            List<String> buildingIds = batch.stream().map(BuildingEntity::getBdrgSn).toList();
            BuildingDataBundle bundle = fetchBundle(buildingIds);

            List<Future<String[]>> futures = batch.stream()
                    .map(building -> executor.submit((Callable<String[]>) () -> computeRow(building, bundle, tradeStatsIndex)))
                    .toList();

            for (Future<String[]> future : futures) {
                writeRow(printer, awaitResult(future));
                total.incrementAndGet();
            }

            entityManager.clear();
            lastBdrgSn = batch.size() == PAGE_SIZE ? batch.get(batch.size() - 1).getBdrgSn() : null;
        }
    }

    // 페이지(최대 1000건) 안의 건물이 필요로 하는 building_id 키 테이블을 IN 절 한 번씩으로 몰아
    // 가져온다 — 건물당 개별 조회(1000건 × 6쿼리)를 페이지당 6쿼리로 줄인다.
    private BuildingDataBundle fetchBundle(List<String> buildingIds) {
        return new BuildingDataBundle(
                groupBy(permitRepository.findByBuildingIdInOrderByPermitDateDesc(buildingIds), PermitEntity::getBuildingId),
                groupBy(landuseRepository.findByBuildingIdIn(buildingIds), LanduseEntity::getBuildingId),
                groupBy(landuseDistrictRepository.findByBuildingIdIn(buildingIds), LanduseDistrictEntity::getBuildingId),
                groupBy(apartmentPriceRepository.findByBuildingIdIn(buildingIds), ApartmentPriceEntity::getBuildingId),
                groupBy(landPriceRepository.findByBuildingIdIn(buildingIds), LandPriceEntity::getBuildingId),
                groupBy(detachedHousePriceRepository.findByBuildingIdIn(buildingIds), DetachedHousePriceEntity::getBuildingId),
                groupBy(tradeRepository.findByBuildingIdInAndCancelDateIsNullOrderByContractDateDesc(buildingIds),
                        TradeEntity::getBuildingId)
        );
    }

    private static <T> Map<String, List<T>> groupBy(List<T> rows, Function<T, String> buildingIdOf) {
        // 원본 조회 순서(예: permit_date/contract_date desc)를 건물별 그룹 안에서도 그대로 보존한다
        // (LinkedHashMap 기반 Collectors.groupingBy는 각 그룹 리스트 안 삽입 순서를 유지).
        return rows.stream().collect(Collectors.groupingBy(buildingIdOf));
    }

    private String[] computeRow(BuildingEntity building, BuildingDataBundle bundle, TradeStatsIndex tradeStatsIndex) {
        InvestmentSnapshot snapshot = investmentService.computeSnapshot(building, bundle, tradeStatsIndex);
        return new String[]{
                building.getBdrgSn(),
                snapshot.investment().grade().getDisplayName(),
                snapshot.investment().roi() == null ? "" : snapshot.investment().roi().toPlainString(),
                writeJson(snapshot.remodeling()),
                writeJson(snapshot.cost()),
                writeJson(snapshot.market())
        };
    }

    // Jackson 3(tools.jackson)부터 writeValueAsString의 JacksonException은 unchecked라 별도 try/catch 불필요.
    private String writeJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private static String[] awaitResult(Future<String[]> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("배치 스레드 인터럽트", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("건물 분석 계산 실패", e.getCause());
        }
    }

    private static void writeRow(CSVPrinter printer, String[] row) {
        try {
            printer.printRecord((Object[]) row);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
