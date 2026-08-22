package com.mteam.rebuildengine.service.board;

import com.mteam.rebuildengine.model.response.DashboardStatsResponse;
import com.mteam.rebuildengine.repository.DashboardStatsSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

// FEATURE_03_BOARD.md — 대시보드 집계는 F-09 배치가 부산물로 만든 스냅샷(dashboard_stats_snapshot)
// 최신 행을 그대로 읽어 반환한다(2026-08-23, product 확정: 실시간 9쿼리 병렬 실행 → 배치+스냅샷으로
// 전환 — 실측 3.2~3.4초는 자주 방문하는 화면 기준으로는 느리고, 후보 정의는 하루 단위로만 바뀌어
// 매 요청 재계산할 이유가 없다는 판단). 스냅샷이 아직 한 번도 없으면(최초 배포 직후) empty.
@Service
@RequiredArgsConstructor
public class DashboardStatsServiceImpl implements DashboardStatsService {

    private final DashboardStatsSnapshotRepository dashboardStatsSnapshotRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<DashboardStatsResponse> getStats() {
        return dashboardStatsSnapshotRepository.findTopByOrderByComputedAtDesc()
                .map(entity -> objectMapper.readValue(entity.getStats(), DashboardStatsResponse.class));
    }
}
