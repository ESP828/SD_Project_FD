package com.example.backend.admin.dto.response;

import java.time.LocalDateTime;

public record AdminPresetResponse(
        Long presetId,
        String title,
        String summary,
        String description,
        String imageUrl,
        String category,
        long viewCount,
        int displayOrder,
        String status,
        long restaurantCount,
        long tagCount,
        long favoriteCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
