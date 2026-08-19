package com.example.backend.review.integration.sentiment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * FastAPI 감성분석 서비스(POST /predict/sentiment/restaurant-summary)의 응답.
 * 추천 점수 계산이나 매장 상세 화면의 "AI 리뷰 분석" 표시에 사용한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RestaurantSentimentSummaryResponse(
        Long restaurantId,
        String restaurantName,
        int reviewCount,
        int positiveCount,
        int negativeCount,
        double positiveRatio
) {
}
