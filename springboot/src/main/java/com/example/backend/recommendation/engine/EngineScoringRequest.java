package com.example.backend.recommendation.engine;

import com.example.backend.restaurant.domain.entity.PublicRestaurant;

import java.util.List;

public record EngineScoringRequest(
        String semanticQuery,
        List<String> tfidfTokens,
        List<PublicRestaurant> candidates
) {
}
