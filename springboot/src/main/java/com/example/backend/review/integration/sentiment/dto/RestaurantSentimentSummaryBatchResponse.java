package com.example.backend.review.integration.sentiment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * FastAPI POST /predict/sentiment/restaurant-summary/batch 응답.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RestaurantSentimentSummaryBatchResponse(
        List<RestaurantSentimentSummaryResponse> items
) {
}
