package com.mteam.rebuildengine.service.search;

import com.mteam.rebuildengine.model.entity.BuildingSummaryEntity;
import com.mteam.rebuildengine.model.response.BuildingSummaryResponse;
import com.mteam.rebuildengine.repository.BuildingRepository;
import com.mteam.rebuildengine.repository.BuildingSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

// FEATURE_05_PROPERTY_INFO.md §2.1 "단지 정보" 카드 / FEATURE_17_BUILDING_SUMMARY_MIGRATION.md.
// building_summary는 F-17이 이미 적재·매칭 완료(18,799/19,000건, 98.9%) — 이 서비스는 조회만 한다.
@Service
@RequiredArgsConstructor
public class BuildingSummaryServiceImpl implements BuildingSummaryService {

    private final BuildingRepository buildingRepository;
    private final BuildingSummaryRepository buildingSummaryRepository;

    @Override
    public Optional<BuildingSummaryResponse> getBuildingSummary(String buildingId) {
        if (buildingRepository.findByBdrgSnAndIsAncillaryFalseAndIsOutOfScopeFalseAndIsDeletedFalse(buildingId).isEmpty()) {
            return Optional.empty();
        }
        List<BuildingSummaryEntity> summaries = buildingSummaryRepository.findByBuildingId(buildingId);
        if (summaries.isEmpty()) {
            return Optional.of(BuildingSummaryResponse.empty());
        }
        BuildingSummaryEntity summary = summaries.get(0);
        return Optional.of(new BuildingSummaryResponse(
                summary.getHouseholdCount(), summary.getMainBuildingCount(),
                summary.getElevatorPassengerCount(), summary.getElevatorEmergencyCount()
        ));
    }
}
