package com.mteam.rebuildengine.mapper;

import com.mteam.rebuildengine.model.read.SearchIndexReadModel;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SearchIndexMapper {
    List<SearchIndexReadModel> search(@Param("keyword") String keyword, @Param("limit") int limit);
}
