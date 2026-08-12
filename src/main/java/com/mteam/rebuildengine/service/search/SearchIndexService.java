package com.mteam.rebuildengine.service.search;

import com.mteam.rebuildengine.model.response.SearchIndexCandidateResponse;

import java.util.List;

public interface SearchIndexService {
    // FEATURE_04 §3.1 GET /api/v1/search-index/search. 후보 개수 상한은 F-04 §5.1 Open Item(10~20건 예시).
    List<SearchIndexCandidateResponse> search(String keyword);
}
