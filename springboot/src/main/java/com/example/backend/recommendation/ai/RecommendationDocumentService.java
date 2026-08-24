package com.example.backend.recommendation.ai;

import com.example.backend.recommendation.evidence.PublicRestaurantEvidence;
import com.example.backend.recommendation.evidence.PublicRestaurantEvidenceRepository;
import com.example.backend.recommendation.model.RecommendationModelStore;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RecommendationDocumentService {

    private final RecommendationModelStore modelStore;
    private final DocumentBuilder documentBuilder;
    private final DocumentV2Builder documentV2Builder;
    private final PublicRestaurantEvidenceRepository evidenceRepository;

    public RecommendationDocumentService(
            RecommendationModelStore modelStore,
            DocumentBuilder documentBuilder,
            DocumentV2Builder documentV2Builder,
            PublicRestaurantEvidenceRepository evidenceRepository
    ) {
        this.modelStore = modelStore;
        this.documentBuilder = documentBuilder;
        this.documentV2Builder = documentV2Builder;
        this.evidenceRepository = evidenceRepository;
    }

    public int tfidfDocumentVersion() {
        Object value = modelStore.getMetadata().get("documentVersion");
        return value instanceof Number number ? number.intValue() : DocumentBuilder.DOCUMENT_VERSION;
    }

    public Map<Long, String> buildTfidfDocuments(List<PublicRestaurant> restaurants) {
        int documentVersion = tfidfDocumentVersion();
        if (documentVersion == DocumentBuilder.DOCUMENT_VERSION) {
            return buildVersionOne(restaurants);
        }
        if (documentVersion != DocumentV2Builder.DOCUMENT_VERSION) {
            throw new IllegalStateException("Unsupported TF-IDF document version: " + documentVersion);
        }

        Map<Long, PublicRestaurantEvidence> evidenceById = evidenceRepository.findByRestaurantIds(
                restaurants.stream().map(PublicRestaurant::getPublicRestaurantId).toList()
        );
        Map<Long, String> result = new LinkedHashMap<>();
        for (PublicRestaurant restaurant : restaurants) {
            Long id = restaurant.getPublicRestaurantId();
            result.put(id, documentV2Builder.build(restaurant, evidenceById.get(id)));
        }
        return Map.copyOf(result);
    }

    private Map<Long, String> buildVersionOne(List<PublicRestaurant> restaurants) {
        Map<Long, String> result = new LinkedHashMap<>();
        for (PublicRestaurant restaurant : restaurants) {
            result.put(restaurant.getPublicRestaurantId(), documentBuilder.build(restaurant));
        }
        return Map.copyOf(result);
    }
}
