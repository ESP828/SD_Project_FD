package com.example.backend.recommendation.ai;

import com.example.backend.recommendation.evidence.PublicRestaurantEvidence;
import com.example.backend.recommendation.evidence.PublicRestaurantEvidenceRepository;
import com.example.backend.recommendation.model.RecommendationModelStore;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationDocumentServiceTest {

    @Test
    void keepsVersionOneDocumentsForTheCurrentTfidfModel() {
        RecommendationModelStore modelStore = mock(RecommendationModelStore.class);
        PublicRestaurantEvidenceRepository repository = mock(PublicRestaurantEvidenceRepository.class);
        when(modelStore.getMetadata()).thenReturn(Map.of("documentVersion", 1));
        PublicRestaurant restaurant = restaurant(1L);
        RecommendationDocumentService service = service(modelStore, repository);

        Map<Long, String> documents = service.buildTfidfDocuments(List.of(restaurant));

        assertThat(documents.get(1L)).isEqualTo("테스트 식당");
        verify(repository, never()).findByRestaurantIds(List.of(1L));
    }

    @Test
    void switchesToEvidenceBackedDocumentsAfterTheV2ModelIsActivated() {
        RecommendationModelStore modelStore = mock(RecommendationModelStore.class);
        PublicRestaurantEvidenceRepository repository = mock(PublicRestaurantEvidenceRepository.class);
        when(modelStore.getMetadata()).thenReturn(Map.of("documentVersion", 2));
        when(repository.findByRestaurantIds(List.of(1L))).thenReturn(Map.of(
                1L,
                new PublicRestaurantEvidence(
                        1L,
                        List.of("SOURCE"),
                        List.of("공공기관 / 공식 데이터"),
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0
                )
        ));
        RecommendationDocumentService service = service(modelStore, repository);

        Map<Long, String> documents = service.buildTfidfDocuments(List.of(restaurant(1L)));

        assertThat(documents.get(1L)).isEqualTo("테스트 식당 주차 가능");
        verify(repository).findByRestaurantIds(List.of(1L));
    }

    private static RecommendationDocumentService service(
            RecommendationModelStore modelStore,
            PublicRestaurantEvidenceRepository repository
    ) {
        DocumentBuilder versionOne = new DocumentBuilder();
        return new RecommendationDocumentService(
                modelStore,
                versionOne,
                new DocumentV2Builder(versionOne),
                repository
        );
    }

    private static PublicRestaurant restaurant(Long id) {
        PublicRestaurant restaurant = new PublicRestaurant("external-" + id, "테스트 식당");
        ReflectionTestUtils.setField(restaurant, "publicRestaurantId", id);
        return restaurant;
    }
}
