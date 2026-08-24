package com.example.backend.recommendation.engine;

public interface RecommendationEngine {

    String engineName();

    EngineScoreResult score(EngineScoringRequest request) throws EngineUnavailableException;
}
