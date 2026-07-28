package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.SearchIndexEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SearchIndexRepository extends JpaRepository<SearchIndexEntity, Long> {
    // ILIKE는 JPQL에 없는 PostgreSQL 전용 연산자라 네이티브 쿼리로 작성 — search_text의
    // GIN(gin_trgm_ops) 인덱스가 이 패턴(ILIKE '%keyword%')을 가속한다(create_search_index_table.sql).
    @Query(value = "SELECT * FROM search_index WHERE search_text ILIKE CONCAT('%', :keyword, '%') " +
            "ORDER BY display_text LIMIT :limit", nativeQuery = true)
    List<SearchIndexEntity> searchByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);
}
