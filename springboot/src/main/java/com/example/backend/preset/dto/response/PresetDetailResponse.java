package com.example.backend.preset.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record PresetDetailResponse(
        Long presetId,
        String title,
        String category,
        long viewCount,
        long favoriteCount,
        boolean favoriteByCurrentUser,
        boolean isOwner,
        String imageUrl,
        LocalDateTime createdAt,
        List<PresetTagResponse> tags,
        List<PresetRestaurantResponse> restaurants
) {
    public PresetDetailResponse {
        tags = List.copyOf(tags);
        restaurants = List.copyOf(restaurants);
    }
}
