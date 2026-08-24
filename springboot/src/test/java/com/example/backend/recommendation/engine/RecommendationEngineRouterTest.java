package com.example.backend.recommendation.engine;

import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationEngineRouterTest {

    @Mock
    private KureRecommendationEngine kureEngine;

    @Mock
    private TfidfRecommendationEngine tfidfEngine;

    @Mock
    private PublicRestaurant candidate;

    private RecommendationEngineRouter router;

    @BeforeEach
    void setUp() {
        router = new RecommendationEngineRouter(kureEngine, tfidfEngine);
    }

    @Test
    void usesKureAsPrimaryEngine() throws Exception {
        EngineScoringRequest request = new EngineScoringRequest("조용한", List.of("조용한"), List.of(candidate));
        EngineScoreResult result =
                new EngineScoreResult("KURE", "nlpai-lab/KURE-v1", "idx-1", 1, Map.of(1L, 0.8));
        when(kureEngine.score(request)).thenReturn(result);

        RoutedEngineResult routed = router.score(request);

        assertThat(routed.engineName()).isEqualTo("KURE");
        assertThat(routed.fallback()).isFalse();
        verify(tfidfEngine, never()).score(request);
    }

    @Test
    void fallsBackToTfidfForTypedKureFailure() throws Exception {
        EngineScoringRequest request = new EngineScoringRequest("조용한", List.of("조용한"), List.of(candidate));
        when(kureEngine.score(request))
                .thenThrow(new EngineUnavailableException("KURE_INDEX_NOT_READY", "not ready"));
        when(tfidfEngine.score(request))
                .thenReturn(new EngineScoreResult("TFIDF", "tfidf-v3", null, 1, Map.of(1L, 0.2)));

        RoutedEngineResult routed = router.score(request);

        assertThat(routed.engineName()).isEqualTo("TFIDF");
        assertThat(routed.fallback()).isTrue();
        assertThat(routed.fallbackReason()).isEqualTo("KURE_INDEX_NOT_READY");
    }

    @Test
    void skipsAllEnginesWhenOnlyDeterministicFiltersRemain() {
        RoutedEngineResult routed =
                router.score(new EngineScoringRequest("", List.of("카페"), List.of(candidate)));

        assertThat(routed.engineName()).isEqualTo("FILTER_ONLY");
        verifyNoInteractions(kureEngine, tfidfEngine);
    }
}
