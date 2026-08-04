package com.example.backend.admin.dto.response;

import com.example.backend.restaurant.domain.entity.Restaurant;

import java.time.LocalDateTime;

public record AdminRestaurantResponse(
        Long restaurantId,
        String name,
        String categoryName,
        String address,
        String addressDetail,
        String phone,
        String openingHours,
        String closedDays,
        String description,
        String status,
        String ownerLoginId,
        String ownerNickname,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminRestaurantResponse from(Restaurant restaurant) {
        return new AdminRestaurantResponse(
                restaurant.getRestaurantId(),
                restaurant.getName(),
                restaurant.getCategory() != null ? restaurant.getCategory().getName() : null,
                restaurant.getAddress(),
                restaurant.getAddressDetail(),
                restaurant.getPhone(),
                restaurant.getOpeningHours(),
                restaurant.getClosedDays(),
                restaurant.getDescription(),
                restaurant.getStatus().name(),
                restaurant.getOwner() != null ? restaurant.getOwner().getLoginId() : null,
                restaurant.getOwner() != null ? restaurant.getOwner().getNickname() : null,
                restaurant.getCreatedAt(),
                restaurant.getUpdatedAt()
        );
    }
}
