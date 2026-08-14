package com.example.backend.business.dto.response;

import com.example.backend.restaurant.domain.entity.Restaurant;

import java.time.LocalDateTime;

public record BusinessRestaurantResponse(
        Long restaurantId,
        Integer categoryId,
        String categoryName,
        String name,
        String address,
        String addressDetail,
        String phone,
        String openingHours,
        String closedDays,
        String description,
        Double latitude,
        Double longitude,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BusinessRestaurantResponse from(Restaurant restaurant) {
        return new BusinessRestaurantResponse(
                restaurant.getRestaurantId(),
                restaurant.getCategory() == null ? null : restaurant.getCategory().getCategoryId(),
                restaurant.getCategory() == null ? null : restaurant.getCategory().getName(),
                restaurant.getName(),
                restaurant.getAddress(),
                restaurant.getAddressDetail(),
                restaurant.getPhone(),
                restaurant.getOpeningHours(),
                restaurant.getClosedDays(),
                restaurant.getDescription(),
                restaurant.getLatitude() == null ? null : restaurant.getLatitude().doubleValue(),
                restaurant.getLongitude() == null ? null : restaurant.getLongitude().doubleValue(),
                restaurant.getStatus().name(),
                restaurant.getCreatedAt(),
                restaurant.getUpdatedAt()
        );
    }
}
