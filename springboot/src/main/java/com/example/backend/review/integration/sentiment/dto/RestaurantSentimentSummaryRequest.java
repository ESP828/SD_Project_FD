package com.example.backend.review.integration.sentiment.dto;

import java.util.List;

public record RestaurantSentimentSummaryRequest(
        Long restaurantId,
        String restaurantName,
        List<String> reviews
) {
}
