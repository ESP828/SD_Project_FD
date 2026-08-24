package com.example.backend.recommendation.engine;

import com.example.backend.recommendation.integration.python.PythonEmbeddingClient;
import com.example.backend.recommendation.integration.python.PythonEmbeddingException;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class KureRecommendationEngine implements RecommendationEngine {

    private final PythonEmbeddingClient pythonEmbeddingClient;

    public KureRecommendationEngine(PythonEmbeddingClient pythonEmbeddingClient) {
        this.pythonEmbeddingClient = pythonEmbeddingClient;
    }

    @Override
    public String engineName() {
        return "KURE";
    }

    @Override
    public EngineScoreResult score(EngineScoringRequest request) throws EngineUnavailableException {
        List<Long> candidateIds = request.candidates().stream()
                .map(PublicRestaurant::getPublicRestaurantId)
                .toList();
        try {
            PythonEmbeddingClient.EmbeddingResult result = pythonEmbeddingClient.search(
                    request.semanticQuery(),
                    candidateIds,
                    candidateIds.size()
            );
            if (result.scores().size() != candidateIds.size()
                    || !result.scores().keySet().equals(Set.copyOf(candidateIds))) {
                throw new EngineUnavailableException(
                        "KURE_PARTIAL_RESPONSE",
                        "KURE did not return a score for every candidate."
                );
            }
            return new EngineScoreResult(
                    engineName(),
                    result.modelName(),
                    result.indexVersion(),
                    result.documentVersion(),
                    result.scores()
            );
        } catch (PythonEmbeddingException exception) {
            throw new EngineUnavailableException(
                    exception.getReasonCode(),
                    exception.getMessage(),
                    exception
            );
        }
    }
}
