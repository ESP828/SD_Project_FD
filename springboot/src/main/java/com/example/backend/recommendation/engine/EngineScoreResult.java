package com.example.backend.recommendation.engine;

import java.util.Map;

public record EngineScoreResult(
        String engineName,
        String modelName,
        String indexVersion,
        Integer documentVersion,
        Map<Long, Double> rawScores
) {
}
