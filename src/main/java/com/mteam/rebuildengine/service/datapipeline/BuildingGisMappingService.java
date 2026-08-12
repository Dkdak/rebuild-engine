package com.mteam.rebuildengine.service.datapipeline;

public interface BuildingGisMappingService {
    // building <-> gis_building 매칭을 계산해 CSV로 내보낸다(DB에 직접 쓰지 않음).
    // 실제 building_gis_mapping 반영은 postgres/sql/load_building_gis_mapping_csv.sql이 담당
    // (F-13/F-14와 동일한 staging+대사처리 패턴, 로컬/서버 어디서든 같은 CSV로 적재 가능).
    MatchResult exportMatchingCsv();

    record MatchResult(int total, int exact, int addressMatch, int scoreBased, int noMatch, int noDongCode) {
    }
}
