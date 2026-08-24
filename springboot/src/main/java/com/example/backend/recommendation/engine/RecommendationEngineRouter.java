package com.example.backend.recommendation.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RecommendationEngineRouter {

    private static final Logger log = LoggerFactory.getLogger(RecommendationEngineRouter.class);

    private final KureRecommendationEngine kureEngine;
    private final TfidfRecommendationEngine tfidfEngine;

    public RecommendationEngineRouter(
            KureRecommendationEngine kureEngine,
            TfidfRecommendationEngine tfidfEngine
    ) {
        this.kureEngine = kureEngine;
        this.tfidfEngine = tfidfEngine;
    }

    public RoutedEngineResult score(EngineScoringRequest request) {
        if (request.candidates().isEmpty() || request.semanticQuery() == null || request.semanticQuery().isBlank()) {
            return RoutedEngineResult.filterOnly();
        }

        try {
            return RoutedEngineResult.primary(kureEngine.score(request));
        } catch (EngineUnavailableException kureFailure) {
            log.warn(
                    "[AI search] KURE unavailable; using TF-IDF fallback. reason={}",
                    kureFailure.getReasonCode()
            );
            try {
                return RoutedEngineResult.fallback(
                        tfidfEngine.score(request),
                        kureFailure.getReasonCode()
                );
            } catch (EngineUnavailableException tfidfFailure) {
                throw new IllegalStateException(
                        "No recommendation scoring engine is available. KURE="
                                + kureFailure.getReasonCode()
                                + ", TFIDF="
                                + tfidfFailure.getReasonCode(),
                        tfidfFailure
                );
            }
        }
    }
}
