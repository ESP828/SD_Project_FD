package com.example.backend.preset.dto.response;

import java.util.List;

public record PresetMapResponse(
        Long presetId,
        String title,
        long favoriteCount,
        boolean favoriteByCurrentUser,
        boolean isOwner,
        List<PresetRestaurantResponse> restaurants
) {
    public PresetMapResponse {
        restaurants = List.copyOf(restaurants);
    }
}
