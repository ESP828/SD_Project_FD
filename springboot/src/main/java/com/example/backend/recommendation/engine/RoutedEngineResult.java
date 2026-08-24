package com.example.backend.recommendation.engine;

import java.util.Map;

public record RoutedEngineResult(
        String engineName,
        String modelName,
        String indexVersion,
        Integer documentVersion,
        Map<Long, Double> rawScores,
        boolean fallback,
        String fallbackReason
) {
    public static RoutedEngineResult primary(EngineScoreResult result) {
        return from(result, false, null);
    }

    public static RoutedEngineResult fallback(EngineScoreResult result, String reason) {
        return from(result, true, reason);
    }

    public static RoutedEngineResult filterOnly() {
        return new RoutedEngineResult("FILTER_ONLY", null, null, null, Map.of(), false, null);
    }

    private static RoutedEngineResult from(EngineScoreResult result, boolean fallback, String reason) {
        return new RoutedEngineResult(
                result.engineName(),
                result.modelName(),
                result.indexVersion(),
                result.documentVersion(),
                result.rawScores(),
                fallback,
                reason
        );
    }
}
