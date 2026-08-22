package com.mteam.rebuildengine.service.favorite;

import com.mteam.rebuildengine.exception.InvalidCredentialsException;
import com.mteam.rebuildengine.model.entity.FavoriteEntity;
import com.mteam.rebuildengine.model.entity.InvestmentResultEntity;
import com.mteam.rebuildengine.model.response.FavoriteListResponse;
import com.mteam.rebuildengine.model.response.PropertyResponse;
import com.mteam.rebuildengine.repository.FavoriteRepository;
import com.mteam.rebuildengine.repository.InvestmentResultRepository;
import com.mteam.rebuildengine.repository.UserRepository;
import com.mteam.rebuildengine.service.search.BuildingService;
import com.mteam.rebuildengine.utils.InvestmentResultResponseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// FEATURE_11_FAVORITES.md §3 — 관심목록. 매물 정보(PropertyResponse)는 F-04와 완전히 같은 조립 방식
// (BuildingService + investment_result)을 재사용한다(§3.2 "신규 스키마를 만들지 않는다").
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final InvestmentResultRepository investmentResultRepository;
    private final BuildingService buildingService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void add(String email, String buildingId) {
        Long userId = resolveUserId(email);
        buildingService.findByBdrgSn(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 건물입니다: " + buildingId));

        InvestmentResultEntity result = investmentResultRepository.findById(buildingId)
                .filter(r -> !r.isDeleted())
                .orElse(null);
        String gradeAtSave = result != null ? result.getGrade().getDisplayName() : null;
        BigDecimal roiAtSave = result != null ? result.getRoi() : null;

        favoriteRepository.findByUserIdAndBuildingId(userId, buildingId)
                .ifPresentOrElse(
                        existing -> existing.reactivate(gradeAtSave, roiAtSave),
                        () -> favoriteRepository.save(FavoriteEntity.builder()
                                .userId(userId)
                                .buildingId(buildingId)
                                .gradeAtSave(gradeAtSave)
                                .roiAtSave(roiAtSave)
                                .build()));
    }

    @Override
    @Transactional
    public void remove(String email, String buildingId) {
        Long userId = resolveUserId(email);
        favoriteRepository.findByUserIdAndBuildingId(userId, buildingId)
                .filter(favorite -> !favorite.isDeleted())
                .ifPresent(FavoriteEntity::markDeleted);
    }

    @Override
    public FavoriteListResponse list(String email, int page, int size) {
        Long userId = resolveUserId(email);
        Page<FavoriteEntity> favorites = favoriteRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(
                userId, PageRequest.of(Math.max(page - 1, 0), size));

        List<String> buildingIds = favorites.getContent().stream().map(FavoriteEntity::getBuildingId).toList();
        Map<String, InvestmentResultEntity> investmentResults = investmentResultRepository
                .findByBuildingIdInAndIsDeletedFalse(buildingIds).stream()
                .collect(Collectors.toMap(InvestmentResultEntity::getBuildingId, Function.identity()));

        List<FavoriteListResponse.FavoriteItem> items = favorites.getContent().stream()
                .map(favorite -> toFavoriteItem(favorite, investmentResults.get(favorite.getBuildingId())))
                .toList();

        return new FavoriteListResponse(items, favorites.getTotalElements(), page, size, favorites.getTotalPages());
    }

    // property가 null이면 배치에서 소프트 삭제된 건물(멸실·재건축 등) — 목록에서 지우지 않고 buildingId·
    // 등록 시점 값만 유지한 채 프론트가 "더 이상 조회할 수 없는 건물"로 표시한다(§4).
    private FavoriteListResponse.FavoriteItem toFavoriteItem(FavoriteEntity favorite, InvestmentResultEntity result) {
        PropertyResponse property = buildingService.findByBdrgSn(favorite.getBuildingId())
                .map(building -> PropertyResponse.from(building,
                        result != null ? result.getGrade().getDisplayName() : null,
                        result != null ? result.getRoi() : null,
                        InvestmentResultResponseMapper.verdict(result, objectMapper),
                        InvestmentResultResponseMapper.estimatedPrice(result, objectMapper)))
                .orElse(null);
        return new FavoriteListResponse.FavoriteItem(favorite.getBuildingId(), property,
                favorite.getGradeAtSave(), favorite.getRoiAtSave(), favorite.getCreatedAt());
    }

    @Override
    public List<String> listIds(String email) {
        return favoriteRepository.findActiveBuildingIdsByUserId(resolveUserId(email));
    }

    private Long resolveUserId(String email) {
        return userRepository.findByEmail(email)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new InvalidCredentialsException("존재하지 않는 계정입니다."))
                .getId();
    }
}
