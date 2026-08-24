package com.example.backend.recommendation.engine;

import org.springframework.stereotype.Component;

/**
 * Reserved extension point only. Gemini is intentionally disconnected and is never a fallback engine.
 */
@Component
public class GeminiRecommendationEngine implements RecommendationEngine {

    @Override
    public String engineName() {
        return "GEMINI";
    }

    @Override
    public EngineScoreResult score(EngineScoringRequest request) {
        throw new UnsupportedOperationException("Gemini recommendation is intentionally disabled.");
    }
}
