package com.example.backend.recommendation.dto.response;

import java.util.List;

public record NaturalLanguageRecommendationResponse(
        String originalQuery,
        ParsedQueryDto parsedQuery,
        List<RecommendedItemDto> items,
        String modelVersion,
        String calculationStatus,
        boolean fallback,
        String engineUsed,
        String indexVersion,
        Integer documentVersion,
        int candidateCount,
        List<String> relaxedFilters,
        List<String> resolvedConstraints,
        String fallbackReason,
        SemanticDiagnostics semanticDiagnostics
) {
    public record SemanticDiagnostics(
            String engineName,
            Double minimumRawScore,
            Double maximumRawScore,
            Double maximumConfidenceAdjustedScore,
            Double confidenceFloor,
            Double confidenceCeiling
    ) {}

    public record ParsedQueryDto(
            String locationText,
            List<String> categories,
            List<String> atmospheres,
            boolean nearby,
            String category,
            String semanticText,
            Integer maxPrice,
            Double minRating,
            String categoryMedium,
            List<String> excludedCategories,
            Integer radiusMeters,
            List<String> unsupportedConstraints,
            boolean locationResolved,
            String resolvedLocationName,
            Double centerLatitude,
            Double centerLongitude
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
            List<String> reasons,
            List<String> evidenceTags,
            List<String> evidenceSources,
            Double semanticRawScore,
            Double semanticScore
    ) {}
}
