package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.service.BuildingBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 배치 수동 트리거 (테스트/검증용) — 정기 실행은 BuildingBatchScheduler가 담당
@RestController
@RequestMapping("/api/v1/admin/buildings")
@RequiredArgsConstructor
public class AdminBuildingController {

    private final BuildingBatchService buildingBatchService;

    // 소규모 구간만 동기화해 빠르게 검증할 때 사용 (예: startIndex=1&endIndex=1000)
    @PostMapping("/sync-range")
    public ResponseEntity<BuildingBatchService.SyncResult> syncRange(
            @RequestParam int startIndex,
            @RequestParam int endIndex
    ) {
        return ResponseEntity.ok(buildingBatchService.syncRange(startIndex, endIndex));
    }

    // 서울 전체 페이지네이션 동기화 (585,331행 기준 약 586회 호출 — 시간이 오래 걸린다)
    @PostMapping("/sync-all")
    public ResponseEntity<BuildingBatchService.SyncResult> syncAll() {
        return ResponseEntity.ok(buildingBatchService.syncAll());
    }
}
