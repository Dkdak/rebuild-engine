package com.mteam.rebuildengine.service;

public interface BuildingBatchService {
    // startIndex~endIndex 구간만 동기화 (테스트/부분 재시도용)
    SyncResult syncRange(int startIndex, int endIndex);

    // 서울 전체를 페이지네이션으로 끝까지 동기화
    SyncResult syncAll();

    record SyncResult(int totalCount, int syncedCount) {
    }
}
