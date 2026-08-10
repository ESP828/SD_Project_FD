package com.example.backend.recommendation.dto.response;

import java.util.List;

public record RecommendationPageResponse(
        List<RecommendedRestaurantResponse> recommendations,
        List<RankedRestaurantResponse> ranking,
        String calculationStatus
) {
    public RecommendationPageResponse {
        recommendations = List.copyOf(recommendations);
        ranking = List.copyOf(ranking);
    }
}
