package com.example.backend.recommendation.dto.response;

public record RankedRestaurantResponse(
        int rank,
        Long restaurantId,
        String restaurantName,
        String categoryName,
        String address,
        String description,
        String imageUrl,
        double averageRating,
        long reviewCount,
        long favoriteCount,
        Double latitude,
        Double longitude
) {
}
