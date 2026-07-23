package com.mteam.rebuildengine.service;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 서울 전체 건축물대장 정기 동기화 (DOMAIN.md §6.4 — 공통 새벽 시간대). 실행 주기는 실데이터 갱신 패턴 관찰 후 조정 예정.
@Component
@RequiredArgsConstructor
public class BuildingBatchScheduler {

    private static final Logger logger = LogManager.getLogger(BuildingBatchScheduler.class);

    private final BuildingBatchService buildingBatchService;

    @Scheduled(cron = "0 0 3 * * *")
    public void runNightlySync() {
        logger.info("건축물대장 배치 시작");
        BuildingBatchService.SyncResult result = buildingBatchService.syncAll();
        logger.info("건축물대장 배치 완료: totalCount={}, syncedCount={}", result.totalCount(), result.syncedCount());
    }
}
