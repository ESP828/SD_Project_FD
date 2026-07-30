package com.example.backend.recommendation.dto.response;

public record BestMenuResponse(
        int rank,
        Long restaurantId,
        String restaurantName,
        String menuName,
        Double latitude,
        Double longitude
) {
}
