package com.mteam.rebuildengine.service;

import com.mteam.rebuildengine.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

// 서울열린데이터광장 OpenAPI(vBigDjrTitle, 건축물대장 표제부) 페이지네이션 호출 전담 클라이언트
// (FEATURE_12_DATA_BATCH.md §B-1). data.go.kr과 별개의 인증 체계 — 인증키는 쿼리 파라미터가 아니라 URL 경로에 들어간다.
@Component
@RequiredArgsConstructor
public class SeoulBuildingClient {

    private static final Logger logger = LogManager.getLogger(SeoulBuildingClient.class);

    private static final String BASE_URL = "http://openapi.seoul.go.kr:8088";
    private static final String SERVICE_NAME = "vBigDjrTitle";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${external.seoul-openapi.service-key}")
    private String serviceKey;

    public Page fetchPage(int startIndex, int endIndex) {
        String url = BASE_URL + "/" + serviceKey.trim() + "/json/" + SERVICE_NAME + "/" + startIndex + "/" + endIndex + "/";

        String rawResponse;
        try {
            rawResponse = restTemplate.getForObject(URI.create(url), String.class);
        } catch (HttpStatusCodeException e) {
            logger.error("서울열린데이터광장 API 응답 본문: {}", e.getResponseBodyAsString());
            throw new ExternalApiException("서울열린데이터광장 API 호출 실패(" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawResponse);
        } catch (Exception e) {
            throw new ExternalApiException("서울열린데이터광장 API 응답을 해석할 수 없습니다.");
        }

        JsonNode service = root.path(SERVICE_NAME);
        String code = service.path("RESULT").path("CODE").asText("");
        if (!code.startsWith("INFO-000")) {
            String message = service.path("RESULT").path("MESSAGE").asText("알 수 없는 오류");
            throw new ExternalApiException("서울열린데이터광장 API 오류(" + code + "): " + message);
        }

        int totalCount = service.path("list_total_count").asInt(0);
        JsonNode rowNode = service.path("row");
        List<JsonNode> rows = new ArrayList<>();
        if (rowNode.isArray()) {
            rowNode.forEach(rows::add);
        }

        return new Page(totalCount, rows);
    }

    public record Page(int totalCount, List<JsonNode> rows) {
    }
}
