package com.example.backend.preset.dto.response;

public record PresetRestaurantCountResponse(
        long restaurantCount,
        int restaurantLimit
) {
}
