package com.mteam.rebuildengine.service;

public interface BuildingGisMappingService {
    MatchResult runMatching();

    record MatchResult(int total, int exact, int addressMatch, int scoreBased, int noMatch, int noDongCode) {
    }
}
