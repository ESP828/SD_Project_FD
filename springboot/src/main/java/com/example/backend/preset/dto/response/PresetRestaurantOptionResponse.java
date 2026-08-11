package com.example.backend.preset.dto.response;

public record PresetRestaurantOptionResponse(
        Long restaurantId,
        String name,
        String categoryName,
        String address,
        String addressDetail
) {
}
