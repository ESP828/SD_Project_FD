package com.example.backend.recommendation.engine;

import com.example.backend.recommendation.ai.RecommendationDocumentService;
import com.example.backend.recommendation.model.RecommendationModelStore;
import com.example.backend.recommendation.score.RecommendationScoreCalculator;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TfidfRecommendationEngine implements RecommendationEngine {

    private final RecommendationModelStore modelStore;
    private final RecommendationScoreCalculator scoreCalculator;
    private final RecommendationDocumentService documentService;

    public TfidfRecommendationEngine(
            RecommendationModelStore modelStore,
            RecommendationScoreCalculator scoreCalculator,
            RecommendationDocumentService documentService
    ) {
        this.modelStore = modelStore;
        this.scoreCalculator = scoreCalculator;
        this.documentService = documentService;
    }

    @Override
    public String engineName() {
        return "TFIDF";
    }

    @Override
    public EngineScoreResult score(EngineScoringRequest request) throws EngineUnavailableException {
        if (!modelStore.isAvailable()) {
            throw new EngineUnavailableException("TFIDF_MODEL_NOT_READY", "TF-IDF fallback model is unavailable.");
        }

        Map<Long, String> documents;
        try {
            documents = documentService.buildTfidfDocuments(request.candidates());
        } catch (RuntimeException exception) {
            throw new EngineUnavailableException(
                    "TFIDF_DOCUMENT_DATA_UNAVAILABLE",
                    "TF-IDF canonical documents could not be built.",
                    exception
            );
        }

        Map<Long, Double> scores = new LinkedHashMap<>();
        for (PublicRestaurant candidate : request.candidates()) {
            scores.put(
                    candidate.getPublicRestaurantId(),
                    scoreCalculator.calculateTextSimilarity(
                            request.tfidfTokens(),
                            documents.get(candidate.getPublicRestaurantId())
                    )
            );
        }
        Map<String, Object> metadata = modelStore.getMetadata();
        Integer documentVersion = documentService.tfidfDocumentVersion();
        return new EngineScoreResult(
                engineName(),
                String.valueOf(metadata.getOrDefault("modelVersion", "fooduck-tfidf")),
                null,
                documentVersion,
                scores
        );
    }
}
