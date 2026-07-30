package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.entity.BuildingEntity;
import com.mteam.rebuildengine.repository.BuildingRepository;
import com.mteam.rebuildengine.utils.InvestmentGrade;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

// F-09(투자 분석) 정식 기획 전 스파이크 테스트용 더미데이터 — bdrg_sn을 시드로 한 Random이라 값이
// 골고루 분포하면서도(진짜 Math.random()과 달리) 재실행해도 같은 건물은 항상 같은 등급/ROI가 나온다
// (같은 건물이 매번 다른 등급으로 나오면 필터 테스트가 의미 없어짐). F-06·F-08 완료 후 정식 F-09
// 기획에서 계산 로직 자체가 통째로 바뀔 예정이라, 지금은 결과가 "그럴듯하게 퍼져 보이는지"만 중요하다.
@Service
@RequiredArgsConstructor
public class InvestmentResultDummyDataServiceImpl implements InvestmentResultDummyDataService {

    private static final Logger logger = LogManager.getLogger(InvestmentResultDummyDataServiceImpl.class);
    private static final int PAGE_SIZE = 1000;
    private static final BigDecimal ROI_MAX = BigDecimal.valueOf(30);

    private final BuildingRepository buildingRepository;
    private final EntityManager entityManager;

    @Value("${data-migration.data-dir}")
    private String dataDir;

    @Override
    public ExportResult exportDummyDataCsv() {
        AtomicInteger total = new AtomicInteger();
        Path outputPath = Path.of(dataDir, "converted", "investment_result_export.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("building_id,grade,roi");
            writer.newLine();

            processPages(building -> {
                writeRow(writer, building.getBdrgSn());
                total.incrementAndGet();
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        ExportResult result = new ExportResult(total.get());
        logger.info("investment_result 더미데이터 CSV 출력 완료: {} ({}건)", outputPath, result.total());
        return result;
    }

    // BuildingGisMappingServiceImpl.processPages와 동일한 keyset pagination — OFFSET 페이징은
    // 585K건 배치에서 페이지가 진행될수록 점점 느려지는 문제가 실측됐다(2026-07-27).
    private void processPages(Consumer<BuildingEntity> consumer) {
        for (String lastBdrgSn = ""; lastBdrgSn != null; ) {
            List<BuildingEntity> batch =
                    buildingRepository.findByBdrgSnGreaterThanOrderByBdrgSnAsc(lastBdrgSn, Pageable.ofSize(PAGE_SIZE));
            batch.forEach(consumer);
            entityManager.clear();
            lastBdrgSn = batch.size() == PAGE_SIZE ? batch.get(batch.size() - 1).getBdrgSn() : null;
        }
    }

    private void writeRow(BufferedWriter writer, String bdrgSn) {
        Random random = new Random(bdrgSn.hashCode());
        InvestmentGrade[] grades = InvestmentGrade.values();
        InvestmentGrade grade = grades[random.nextInt(grades.length)];
        BigDecimal roi = ROI_MAX.multiply(BigDecimal.valueOf(random.nextDouble())).setScale(2, RoundingMode.HALF_UP);
        try {
            writer.write(bdrgSn + "," + grade.getDisplayName() + "," + roi);
            writer.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
