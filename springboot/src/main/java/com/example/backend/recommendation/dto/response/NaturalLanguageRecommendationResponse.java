package com.example.backend.recommendation.dto.response;

import java.util.List;

public record NaturalLanguageRecommendationResponse(
        String originalQuery,
        ParsedQueryDto parsedQuery,
        List<RecommendedItemDto> items,
        String modelVersion,
        String calculationStatus,
        boolean fallback
) {
    public record ParsedQueryDto(
            String locationText,
            List<String> categories,
            List<String> atmospheres,
            boolean nearby
    ) {}

    public record RecommendedItemDto(
            String sourceType,
            Long sourceId,
            String restaurantName,
            String categoryName,
            String address,
            Double latitude,
            Double longitude,
            Double distanceMeters,
            Double score,
            List<String> reasons
    ) {}
}
