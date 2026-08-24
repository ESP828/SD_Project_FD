package com.example.backend.recommendation.integration.python;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.timeoutMs = timeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public EmbeddingResult search(String query, List<Long> restaurantIds, int topK)
            throws PythonEmbeddingException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("query", query);
        request.put("restaurantIds", restaurantIds);
        request.put("topK", topK);
        return post("/embedding/search", request);
    }

    public EmbeddingResult scoreFavorites(List<Long> favoriteRestaurantIds, List<Long> candidateRestaurantIds)
            throws PythonEmbeddingException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("favoriteRestaurantIds", favoriteRestaurantIds);
        request.put("candidateRestaurantIds", candidateRestaurantIds);
        return post("/embedding/favorites", request);
    }

    private EmbeddingResult post(String path, Map<String, Object> requestBody)
            throws PythonEmbeddingException {
        try {
            String json = objectMapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() != 200) {
                throw responseException(response);
            }
            return parseResult(response.body());
        } catch (PythonEmbeddingException exception) {
            throw exception;
        } catch (HttpTimeoutException exception) {
            throw new PythonEmbeddingException("KURE_TIMEOUT", "KURE request timed out.", exception);
        } catch (ConnectException exception) {
            throw new PythonEmbeddingException("KURE_CONNECTION_REFUSED", "KURE service is unavailable.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PythonEmbeddingException("KURE_INTERRUPTED", "KURE request was interrupted.", exception);
        } catch (Exception exception) {
            throw new PythonEmbeddingException("KURE_INVALID_RESPONSE", exception.getMessage(), exception);
        }
    }

    private PythonEmbeddingException responseException(HttpResponse<String> response) {
        String code = "KURE_HTTP_" + response.statusCode();
        String message = "KURE service returned HTTP " + response.statusCode() + ".";
        try {
            JsonNode body = objectMapper.readTree(response.body());
            if (body.hasNonNull("code")) {
                code = body.get("code").asText();
            }
            if (body.hasNonNull("message")) {
                message = body.get("message").asText();
            }
        } catch (Exception ignored) {
            // The HTTP status remains enough to make a deterministic fallback decision.
        }
        return new PythonEmbeddingException(code, message);
    }

    private EmbeddingResult parseResult(String responseBody) throws Exception {
        JsonNode body = objectMapper.readTree(responseBody);
        if (!body.hasNonNull("engine") || !body.path("items").isArray()) {
            throw new PythonEmbeddingException("KURE_INVALID_RESPONSE", "KURE response contract is invalid.");
        }

        Map<Long, Double> scores = new LinkedHashMap<>();
        for (JsonNode item : body.path("items")) {
            if (!item.hasNonNull("id") || !item.hasNonNull("score")) {
                throw new PythonEmbeddingException("KURE_INVALID_RESPONSE", "KURE score item is invalid.");
            }
            scores.put(item.get("id").longValue(), item.get("score").doubleValue());
        }
        return new EmbeddingResult(
                body.path("engine").asText("KURE"),
                body.path("modelName").isMissingNode() ? null : body.path("modelName").asText(null),
                body.path("indexVersion").isMissingNode() ? null : body.path("indexVersion").asText(null),
                body.path("documentVersion").isInt() ? body.path("documentVersion").intValue() : null,
                scores
        );
    }

    public record EmbeddingResult(
            String engine,
            String modelName,
            String indexVersion,
            Integer documentVersion,
            Map<Long, Double> scores
    ) {
    }
}
