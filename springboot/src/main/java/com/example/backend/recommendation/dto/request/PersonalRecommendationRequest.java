package com.example.backend.recommendation.dto.request;

public record PersonalRecommendationRequest(
    Long accountId,
    Integer age,
    String gender,
    Double latitude,
    Double longitude,
    Double radiusMeters,
    int limit
) {}
