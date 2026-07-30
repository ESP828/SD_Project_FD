package com.example.backend.recommendation.dto.response;

public record RecommendedRestaurantResponse(
        Long restaurantId,
        String restaurantName,
        String categoryName,
        String menuName,
        Integer menuPrice,
        String imageUrl,
        String reason,
        Double latitude,
        Double longitude
) {
}
