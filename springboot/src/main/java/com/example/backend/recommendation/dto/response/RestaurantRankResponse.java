package com.example.backend.recommendation.dto.response;

public record RestaurantRankResponse(
        Long restaurantId,
        String name,
        String category,
        String address,
        Double rawRating,
        Integer reviewCount,
        Integer favoriteCount,
        Double aiSentimentPositiveRatio,
        Double finalRankScore
) {}
