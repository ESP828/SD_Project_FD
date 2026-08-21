package com.example.backend.review.integration.sentiment;

import com.example.backend.review.integration.sentiment.dto.RestaurantSentimentSummaryBatchRequest;
import com.example.backend.review.integration.sentiment.dto.RestaurantSentimentSummaryBatchResponse;
import com.example.backend.review.integration.sentiment.dto.RestaurantSentimentSummaryRequest;
import com.example.backend.review.integration.sentiment.dto.RestaurantSentimentSummaryResponse;
import com.example.backend.review.integration.sentiment.dto.SentimentPredictionRequest;
import com.example.backend.review.integration.sentiment.dto.SentimentPredictionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 리뷰 감성분석(Naive Bayes) FastAPI 서비스(springboot/ai) 호출을 담당한다.
 * sentiment-api.base-url이 비어 있으면(로컬에서 FastAPI를 안 띄운 경우 등)
 * isConfigured()가 false를 반환하므로, 호출부는 이걸 먼저 확인하고 기능을 건너뛰어야 한다.
 */
@Component
public class SentimentAnalysisClient {

    private final RestClient restClient;
    private final String baseUrl;

    public SentimentAnalysisClient(
            RestClient.Builder restClientBuilder,
            @Value("${sentiment-api.base-url:}") String baseUrl
    ) {
        this.restClient = restClientBuilder.build();
        this.baseUrl = baseUrl;
    }

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    /**
     * 리뷰 한 건의 긍정/부정을 예측한다.
     */
    public SentimentPredictionResponse predict(String review) {
        return restClient.post()
                .uri(baseUrl + "/predict/sentiment")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SentimentPredictionRequest(review))
                .retrieve()
                .body(SentimentPredictionResponse.class);
    }

    /**
     * 한 매장의 리뷰 여러 건을 한 번에 분석해서 긍정/부정 개수·비율을 집계한다.
     */
    public RestaurantSentimentSummaryResponse summarizeRestaurant(
            Long restaurantId, String restaurantName, List<String> reviews
    ) {
        return restClient.post()
                .uri(baseUrl + "/predict/sentiment/restaurant-summary")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RestaurantSentimentSummaryRequest(restaurantId, restaurantName, reviews))
                .retrieve()
                .body(RestaurantSentimentSummaryResponse.class);
    }

    /**
     * 매장 여러 곳의 리뷰를 한 번의 호출로 분석한다(맛집 랭킹 화면처럼 매장이 많을 때
     * 매장마다 따로 호출하면 N+1 HTTP 호출이 되므로 배치로 처리한다).
     */
    public List<RestaurantSentimentSummaryResponse> summarizeRestaurants(
            List<RestaurantSentimentSummaryRequest> items
    ) {
        if (items.isEmpty()) {
            return List.of();
        }
        RestaurantSentimentSummaryBatchResponse response = restClient.post()
                .uri(baseUrl + "/predict/sentiment/restaurant-summary/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RestaurantSentimentSummaryBatchRequest(items))
                .retrieve()
                .body(RestaurantSentimentSummaryBatchResponse.class);
        return response != null && response.items() != null ? response.items() : List.of();
    }
}
