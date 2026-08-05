package com.example.backend.preset.dto.response;

public record PresetRestaurantResponse(
        Long restaurantId,
        String name,
        String categoryName,
        String address,
        String addressDetail,
        String phone,
        String openingHours,
        String closedDays,
        String description,
        String presetDescription,
        String imageUrl,
        double averageRating,
        long reviewCount,
        Double latitude,
        Double longitude,
        boolean favoriteByCurrentUser,
        boolean coordinateAvailable
) {
}
