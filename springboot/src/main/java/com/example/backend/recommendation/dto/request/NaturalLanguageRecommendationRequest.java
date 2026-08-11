package com.example.backend.recommendation.dto.request;

public record NaturalLanguageRecommendationRequest(
        String query,
        Double latitude,
        Double longitude,
        Integer radiusMeters,
        Integer limit
) {
    public Integer radiusMeters() {
        return radiusMeters != null ? radiusMeters : 1500; // 기본 반경 1.5km
    }

    public Integer limit() {
        return limit != null ? limit : 5; // 기본 5개 추천
    }
}
