package com.example.backend.recommendation.integration.python;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * springboot/ai(FastAPI + SentenceTransformer)의 의미 유사도 검색 API를 호출한다.
 * 서비스가 꺼져 있거나 오류가 나면 예외를 던지며, 호출부(RecommendationService)가
 * 기존 TF-IDF 계산으로 자동 폴백한다.
 */
@Component
public class PythonEmbeddingClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final long timeoutMs;

    public PythonEmbeddingClient(
            @Value("${recommendation.ai.base-url:http://127.0.0.1:8000}") String baseUrl,
            @Value("${recommendation.ai.timeout-ms:4000}") long timeoutMs
    ) {
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * 후보 음식점 id 목록 안에서 질의어와의 의미 유사도(0.0~1.0)를 조회한다.
     */
    public Map<Long, Double> search(String query, List<Long> restaurantIds, int topK) throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("query", query);
        requestBody.put("restaurantIds", restaurantIds);
        requestBody.put("topK", topK);

        String json = objectMapper.writeValueAsString(requestBody);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/embedding/search"))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("AI 임베딩 서비스 응답 오류: HTTP " + response.statusCode());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");

        Map<Long, Double> scores = new LinkedHashMap<>();
        if (items != null) {
            for (Map<String, Object> item : items) {
                Long id = ((Number) item.get("id")).longValue();
                Double score = ((Number) item.get("score")).doubleValue();
                scores.put(id, score);
            }
        }
        return scores;
    }
}
