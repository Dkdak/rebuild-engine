package com.mteam.rebuildengine.model.response;

// F-06 §3.2-4/5 — 게이트(노후도·인허가 진행중)에 걸리면 점수와 무관하게 항상 NOT_POSSIBLE.
// 게이트 통과 시에만 점수 구간으로 POSSIBLE/LIMITED를 가른다.
public enum RemodelingVerdict {
    POSSIBLE,
    LIMITED,
    NOT_POSSIBLE
}
