package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.model.request.FavoriteAddRequest;
import com.mteam.rebuildengine.model.response.FavoriteListResponse;
import com.mteam.rebuildengine.service.favorite.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// FEATURE_11_FAVORITES.md §3.2 — 관심목록. 전 API 로그인 필수(SecurityConfig, 비로그인 401).
@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;

    private final FavoriteService favoriteService;

    @PostMapping
    public ResponseEntity<Void> add(Authentication authentication, @RequestBody FavoriteAddRequest request) {
        favoriteService.add(authentication.getName(), request.buildingId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{buildingId}")
    public ResponseEntity<Void> remove(Authentication authentication, @PathVariable String buildingId) {
        favoriteService.remove(authentication.getName(), buildingId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<FavoriteListResponse> list(Authentication authentication,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        int resolvedPage = page != null ? page : DEFAULT_PAGE;
        int resolvedSize = size != null ? size : DEFAULT_SIZE;
        return ResponseEntity.ok(favoriteService.list(authentication.getName(), resolvedPage, resolvedSize));
    }

    @GetMapping("/ids")
    public ResponseEntity<List<String>> ids(Authentication authentication) {
        return ResponseEntity.ok(favoriteService.listIds(authentication.getName()));
    }
}
