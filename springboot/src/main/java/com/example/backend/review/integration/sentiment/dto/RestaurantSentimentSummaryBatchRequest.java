package com.example.backend.review.integration.sentiment.dto;

import java.util.List;

/**
 * FastAPI POST /predict/sentiment/restaurant-summary/batch 요청 바디.
 * 매장 여러 곳의 리뷰를 한 번의 호출로 분석해서 N+1 HTTP 호출을 피한다.
 */
public record RestaurantSentimentSummaryBatchRequest(
        List<RestaurantSentimentSummaryRequest> items
) {
}
