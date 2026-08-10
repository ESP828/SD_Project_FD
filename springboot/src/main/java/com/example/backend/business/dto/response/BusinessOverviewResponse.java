package com.example.backend.business.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record BusinessOverviewResponse(
        long restaurantCount,
        long activeRestaurantCount,
        long newsCount,
        long reviewCount,
        long favoriteCount,
        List<RestaurantSummary> restaurants
) {
    public BusinessOverviewResponse {
        restaurants = List.copyOf(restaurants);
    }

    public record RestaurantSummary(
            Long restaurantId,
            String name,
            String categoryName,
            String status,
            String address,
            long newsCount,
            long reviewCount,
            long favoriteCount,
            LocalDateTime createdAt
    ) {
    }
}
