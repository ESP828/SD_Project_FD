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
            // 리뷰가 있는 매장에 한해 카카오 이미지 검색으로 캐싱한 대표 이미지. 없으면 null -
            // 프론트에서는 이때 카테고리 마커 아이콘으로 대체한다.
            String imageUrl,
            List<String> evidenceTags,
            List<String> evidenceSources,
            Double semanticRawScore,
            Double semanticScore
    ) {}
}
