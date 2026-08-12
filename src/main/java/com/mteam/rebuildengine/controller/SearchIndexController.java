package com.mteam.rebuildengine.controller;

import com.mteam.rebuildengine.model.response.SearchIndexCandidateResponse;
import com.mteam.rebuildengine.service.search.SearchIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// FEATURE_04 §1.2·§3.1 통합 검색 — search_index를 ILIKE로 조회, 외부 API 호출 없음.
@RestController
@RequestMapping("/api/v1/search-index")
@RequiredArgsConstructor
public class SearchIndexController {

    private final SearchIndexService searchIndexService;

    @GetMapping("/search")
    public ResponseEntity<List<SearchIndexCandidateResponse>> search(@RequestParam String keyword) {
        return ResponseEntity.ok(searchIndexService.search(keyword));
    }
}
