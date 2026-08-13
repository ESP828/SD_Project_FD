package com.example.backend.preset.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record PresetSummaryResponse(
        Long presetId,
        String title,
        String category,
        String imageUrl,
        long viewCount,
        int displayOrder,
        long restaurantCount,
        long favoriteCount,
        boolean favoriteByCurrentUser,
        boolean isOwner,
        LocalDateTime createdAt,
        List<PresetTagResponse> tags,
        List<String> thumbnailImageUrls
) {
    public PresetSummaryResponse {
        tags = List.copyOf(tags);
        thumbnailImageUrls = List.copyOf(thumbnailImageUrls);
    }
}
