package com.mteam.rebuildengine.model.response;

// HELP2.md §2 "에러 응답은 일관된 구조 유지" 기준의 공통 에러 바디
public class ErrorResponse {
    private final String message;

    public ErrorResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
