package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.model.response.SearchIndexCandidateResponse;
import com.mteam.rebuildengine.repository.SearchIndexRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchIndexServiceImpl implements SearchIndexService {

    private static final int CANDIDATE_LIMIT = 20;

    // pg_trgm은 3글자 단위 트라이그램이라 검색어가 2글자 미만이면 인덱스 효율이 크게 떨어진다.
    private static final int MIN_KEYWORD_LENGTH = 2;

    private final SearchIndexRepository searchIndexRepository;

    @Override
    public List<SearchIndexCandidateResponse> search(String keyword) {
        if (keyword == null || keyword.strip().length() < MIN_KEYWORD_LENGTH) {
            return List.of();
        }
        return searchIndexRepository.searchByKeyword(keyword, CANDIDATE_LIMIT).stream()
                .map(SearchIndexCandidateResponse::from)
                .toList();
    }
}
