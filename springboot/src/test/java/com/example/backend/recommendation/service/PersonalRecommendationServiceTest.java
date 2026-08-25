package com.example.backend.recommendation.service;

import com.example.backend.favorite.query.PublicRestaurantFavoriteQueryRepository;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.recommendation.ai.RecommendationDocumentService;
import com.example.backend.recommendation.dto.response.PersonalRecommendationResponse;
import com.example.backend.recommendation.dto.response.PersonalRecommendedItemDto;
import com.example.backend.recommendation.engine.RecommendationEngineRouter;
import com.example.backend.recommendation.integration.kakao.KakaoLocalGeocodingClient;
import com.example.backend.recommendation.integration.python.PythonEmbeddingClient;
import com.example.backend.recommendation.integration.python.PythonEmbeddingException;
import com.example.backend.recommendation.preference.PersonalPreferenceService;
import com.example.backend.recommendation.preference.domain.PersonalPreferenceProfile;
import com.example.backend.recommendation.preference.domain.WeightedRestaurantSignal;
import com.example.backend.recommendation.query.PublicRecommendationQueryRepository;
import com.example.backend.recommendation.query.RecommendationQueryRepository;
import com.example.backend.recommendation.score.RecommendationScoreCalculator;
import com.example.backend.recommendation.score.RecommendationScoreNormalizer;
import com.example.backend.recommendation.text.RecommendationQueryParser;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import com.example.backend.review.integration.sentiment.SentimentAnalysisClient;
import com.example.backend.review.repository.ReviewRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PersonalRecommendationServiceTest {

    private static final long ACCOUNT_ID = 7L;
    private static final double LATITUDE = 37.4979;
    private static final double LONGITUDE = 127.0276;
    private static final AuthenticatedAccount ACCOUNT =
            new AuthenticatedAccount(ACCOUNT_ID, "personal-test", List.of("ROLE_USER"));

    @Test
    void returnsFavoriteGuidanceWithoutLookingUpCandidatesWhenOnlyPositiveReviewExists() {
        Fixture fixture = new Fixture();
        fixture.givenProfile(profileWithPositiveRatingOnly(10L));

        PersonalRecommendationResponse response = fixture.service.recommendForUser(
                ACCOUNT, LATITUDE, LONGITUDE, 3_000.0, 10
        );

        assertThat(response.personalizationLevel()).isEqualTo("NO_FAVORITES");
        assertThat(response.userPreferenceSummary()).contains("찜한 맛집");
        assertThat(response.items()).isEmpty();
        verifyNoInteractions(
                fixture.candidateFilterService,
                fixture.restaurantQualityService,
                fixture.pythonEmbeddingClient
        );
    }

    @Test
    void excludesExperiencedRestaurantsBeforeRequestingKureScores() throws Exception {
        Fixture fixture = new Fixture();
        PersonalPreferenceProfile profile = profileWithFavoriteAndNegativeRating(10L, 30L);
        fixture.givenProfile(profile);
        fixture.givenCandidates(
                restaurant(10L, "이미 방문", "일식"),
                restaurant(20L, "새 후보", "일식")
        );
        fixture.givenQuality(20L, 0L, null, 0.70);
        when(fixture.pythonEmbeddingClient.scorePersonalProfile(
                eq(profile.positiveSignals()), eq(profile.negativeSignals()), eq(List.of(20L))))
                .thenReturn(new PythonEmbeddingClient.EmbeddingResult(
                        "KURE", "test", "test-index", 1, Map.of(20L, 0.9)
                ));

        PersonalRecommendationResponse response = fixture.service.recommendForUser(
                ACCOUNT, LATITUDE, LONGITUDE, 3_000.0, 10
        );

        assertThat(response.items()).extracting(item -> item.restaurantId())
                .containsExactly(20L);
        assertThat(response.items().get(0).distanceScore()).isNull();
        assertThat(response.items().get(0).reasons())
                .noneMatch(reason -> reason.contains("낮게 평가한") || reason.contains("가까운"));
        verify(fixture.restaurantQualityService).scoreAll(List.of(20L));
    }

    @Test
    void doesNotUseDistanceToBreakScoreTies() throws Exception {
        Fixture fixture = new Fixture();
        PersonalPreferenceProfile profile = profileWithFavorite(10L);
        PublicRestaurant far = restaurant(
                20L, "먼 후보", "일식", LATITUDE + 0.01, LONGITUDE
        );
        PublicRestaurant near = restaurant(21L, "가까운 후보", "일식");
        fixture.givenProfile(profile);
        fixture.givenCandidates(near, far);
        // 후보는 거리 순으로 들어오지만, 품질 집계는 취향 상위 순서(동점이면 ID 순)로 넘어간다.
        when(fixture.restaurantQualityService.scoreAll(List.of(20L, 21L))).thenReturn(Map.of(
                20L, new RestaurantQualityService.RestaurantQuality(20L, 0L, null, 0.70),
                21L, new RestaurantQualityService.RestaurantQuality(21L, 0L, null, 0.70)
        ));
        when(fixture.pythonEmbeddingClient.scorePersonalProfile(
                eq(profile.positiveSignals()), eq(profile.negativeSignals()), eq(List.of(21L, 20L))))
                .thenReturn(new PythonEmbeddingClient.EmbeddingResult(
                        "KURE", "test", "test-index", 1, Map.of(20L, 0.8, 21L, 0.8)
                ));

        PersonalRecommendationResponse response = fixture.service.recommendForUser(
                ACCOUNT, LATITUDE, LONGITUDE, 3_000.0, 10
        );

        assertThat(response.items()).extracting(item -> item.restaurantId())
                .containsExactly(20L, 21L);
        assertThat(response.items().get(0).score()).isEqualTo(response.items().get(1).score());
        assertThat(response.items()).allMatch(item -> item.distanceScore() == null);
        assertThat(response.items().get(0).distanceMeters())
                .isGreaterThan(response.items().get(1).distanceMeters());
    }

    @Test
    void excludesCandidatesThatAreOnlyRelativeWinnersWithWeakTasteSimilarity() throws Exception {
        Fixture fixture = new Fixture();
        PersonalPreferenceProfile profile = profileWithFavorite(10L);
        fixture.givenProfile(profile);
        fixture.givenCandidates(restaurant(20L, "가깝지만 무관한 후보", "카페·디저트"));
        when(fixture.pythonEmbeddingClient.scorePersonalProfile(
                eq(profile.positiveSignals()), eq(profile.negativeSignals()), eq(List.of(20L))))
                .thenReturn(new PythonEmbeddingClient.EmbeddingResult(
                        "KURE", "test", "test-index", 1, Map.of(20L, 0.1)
                ));

        PersonalRecommendationResponse response = fixture.service.recommendForUser(
                ACCOUNT, LATITUDE, LONGITUDE, 3_000.0, 10
        );

        assertThat(response.items()).isEmpty();
        assertThat(response.userPreferenceSummary()).contains("충분히 비슷한");
        verifyNoInteractions(fixture.restaurantQualityService);
    }

    @Test
    void fallsBackToTfidfAndUsesPositiveRestaurantDocumentsWhenKureFails() throws Exception {
        Fixture fixture = new Fixture();
        PersonalPreferenceProfile profile = profileWithFavorite(10L);
        PublicRestaurant liked = restaurant(10L, "초밥집", "일식");
        PublicRestaurant candidate = restaurant(20L, "라멘집", "일식");
        fixture.givenProfile(profile);
        fixture.givenCandidates(candidate);
        fixture.givenQuality(20L, 0L, null, 0.70);

        when(fixture.pythonEmbeddingClient.scorePersonalProfile(
                eq(profile.positiveSignals()), eq(profile.negativeSignals()), eq(List.of(20L))))
                .thenThrow(new PythonEmbeddingException("KURE_TIMEOUT", "timeout"));
        when(fixture.publicQueryRepository.findAllById(List.of(10L))).thenReturn(List.of(liked));
        when(fixture.documentService.buildTfidfDocuments(anyList())).thenReturn(Map.of(
                10L, "초밥 연어 일식",
                20L, "라멘 일식"
        ));
        when(fixture.scoreCalculator.calculateTextSimilarity(
                org.mockito.ArgumentMatchers.argThat(tokens -> tokens.contains("연어")),
                eq("라멘 일식")
        )).thenReturn(0.8);

        PersonalRecommendationResponse response = fixture.service.recommendForUser(
                ACCOUNT, LATITUDE, LONGITUDE, 3_000.0, 10
        );

        assertThat(response.items()).hasSize(1);
        // shortlist가 한 건이면 상대 순위를 매길 대상이 없으므로 절대 신뢰도만 남는다.
        assertThat(response.items().get(0).tasteScore()).isEqualTo(1.0);
        // 사유는 계정 상태가 아니라 이 매장의 실제 취향 점수에서 나온다.
        assertThat(response.items().get(0).reasons())
                .contains("찜한 맛집과 취향이 매우 비슷합니다.");
        verify(fixture.scoreCalculator).calculateTextSimilarity(
                org.mockito.ArgumentMatchers.argThat(tokens -> tokens.contains("연어")),
                eq("라멘 일식")
        );
    }

    @Test
    void buildsReasonsFromEachRestaurantScoreInsteadOfTheAccountState() throws Exception {
        Fixture fixture = new Fixture();
        PersonalPreferenceProfile profile = profileWithFavorite(10L);
        fixture.givenProfile(profile);
        fixture.givenCandidates(
                restaurant(20L, "아주 비슷한 후보", "일식"),
                restaurant(21L, "겨우 걸친 후보", "일식"),
                restaurant(22L, "취향과 먼 후보", "일식")
        );
        when(fixture.restaurantQualityService.scoreAll(List.of(20L, 21L, 22L))).thenReturn(Map.of(
                20L, new RestaurantQualityService.RestaurantQuality(20L, 0L, null, 0.70),
                21L, new RestaurantQualityService.RestaurantQuality(21L, 0L, null, 0.70),
                22L, new RestaurantQualityService.RestaurantQuality(22L, 0L, null, 0.70)
        ));
        when(fixture.pythonEmbeddingClient.scorePersonalProfile(
                eq(profile.positiveSignals()), eq(profile.negativeSignals()),
                eq(List.of(20L, 21L, 22L))))
                .thenReturn(new PythonEmbeddingClient.EmbeddingResult(
                        "KURE", "test", "test-index", 1, Map.of(20L, 0.60, 21L, 0.45, 22L, 0.30)
                ));

        PersonalRecommendationResponse response = fixture.service.recommendForUser(
                ACCOUNT, LATITUDE, LONGITUDE, 3_000.0, 10
        );

        assertThat(response.items()).extracting(item -> item.restaurantId())
                .containsExactly(20L, 21L, 22L);
        assertThat(response.items().get(0).reasons())
                .contains("찜한 맛집과 취향이 매우 비슷합니다.");
        assertThat(response.items().get(1).reasons())
                .contains("찜한 맛집과 취향이 비슷합니다.");
        assertThat(response.items().get(2).reasons())
                .contains("찜한 맛집 취향과 일부 겹칩니다.");
    }

    @Test
    void spreadsTasteScoresInsideTheShortlistSoQualityCanStillMatter() throws Exception {
        Fixture fixture = new Fixture();
        PersonalPreferenceProfile profile = profileWithFavorite(10L);
        fixture.givenProfile(profile);
        fixture.givenCandidates(
                restaurant(20L, "가장 비슷한 후보", "일식"),
                restaurant(21L, "거의 비슷한 후보", "일식"),
                restaurant(22L, "덜 비슷한 후보", "일식")
        );
        when(fixture.restaurantQualityService.scoreAll(List.of(20L, 21L, 22L))).thenReturn(Map.of(
                20L, new RestaurantQualityService.RestaurantQuality(20L, 0L, null, 0.70),
                21L, new RestaurantQualityService.RestaurantQuality(21L, 0L, null, 0.70),
                22L, new RestaurantQualityService.RestaurantQuality(22L, 0L, null, 0.70)
        ));
        // KURE 코사인은 상위권이 촘촘하다. 후보 전체 percentile을 쓰면 이 간격이 사라진다.
        when(fixture.pythonEmbeddingClient.scorePersonalProfile(
                eq(profile.positiveSignals()), eq(profile.negativeSignals()),
                eq(List.of(20L, 21L, 22L))))
                .thenReturn(new PythonEmbeddingClient.EmbeddingResult(
                        "KURE", "test", "test-index", 1, Map.of(20L, 0.80, 21L, 0.77, 22L, 0.74)
                ));

        PersonalRecommendationResponse response = fixture.service.recommendForUser(
                ACCOUNT, LATITUDE, LONGITUDE, 3_000.0, 10
        );

        List<Double> tasteScores = response.items().stream()
                .map(PersonalRecommendedItemDto::tasteScore)
                .toList();
        assertThat(tasteScores).hasSize(3);
        assertThat(tasteScores.get(0)).isGreaterThan(tasteScores.get(1));
        assertThat(tasteScores.get(1)).isGreaterThan(tasteScores.get(2));
        // 상위권이 전부 1.0 근처로 뭉치면 품질 신호가 순위에 개입할 수 없다.
        assertThat(tasteScores.get(0) - tasteScores.get(2)).isGreaterThan(0.1);
    }

    @Test
    void keepsOneFranchiseFromTakingOverTheWholeResult() throws Exception {
        Fixture fixture = new Fixture();
        PersonalPreferenceProfile profile = profileWithFavorite(10L);
        fixture.givenProfile(profile);
        // 지점명이 붙은 이름끼리는 서로 접두어가 아니다. 접두 "포함" 관계만 보면 그대로 새어 나간다.
        fixture.givenCandidates(
                restaurant(20L, "메가엠지씨커피강남", "카페·디저트"),
                restaurant(21L, "메가엠지씨커피논현역점", "카페·디저트"),
                restaurant(22L, "메가엠지씨커피가로수길점", "카페·디저트"),
                restaurant(23L, "카페포엠", "카페·디저트")
        );
        when(fixture.restaurantQualityService.scoreAll(List.of(20L, 21L, 22L, 23L))).thenReturn(Map.of(
                20L, new RestaurantQualityService.RestaurantQuality(20L, 0L, null, 0.70),
                21L, new RestaurantQualityService.RestaurantQuality(21L, 0L, null, 0.70),
                22L, new RestaurantQualityService.RestaurantQuality(22L, 0L, null, 0.70),
                23L, new RestaurantQualityService.RestaurantQuality(23L, 0L, null, 0.70)
        ));
        when(fixture.pythonEmbeddingClient.scorePersonalProfile(
                eq(profile.positiveSignals()), eq(profile.negativeSignals()),
                eq(List.of(20L, 21L, 22L, 23L))))
                .thenReturn(new PythonEmbeddingClient.EmbeddingResult(
                        "KURE", "test", "test-index", 1,
                        Map.of(20L, 0.80, 21L, 0.79, 22L, 0.78, 23L, 0.77)
                ));

        PersonalRecommendationResponse response = fixture.service.recommendForUser(
                ACCOUNT, LATITUDE, LONGITUDE, 3_000.0, 10
        );

        // 점수만 보면 같은 브랜드 3곳이 앞자리를 모두 차지한다. 사용자에게는 한 곳을 추천한 것과 같다.
        assertThat(response.items()).extracting(item -> item.restaurantId())
                .containsExactly(20L, 21L, 23L);
    }

    @Test
    void dropsCandidatesBelowThePersonalKureFloor() throws Exception {
        Fixture fixture = new Fixture();
        PersonalPreferenceProfile profile = profileWithFavorite(10L);
        fixture.givenProfile(profile);
        fixture.givenCandidates(
                restaurant(20L, "취향에 맞는 후보", "일식"),
                restaurant(21L, "취향과 먼 후보", "일식")
        );
        fixture.givenQuality(20L, 0L, null, 0.70);
        // 개인화 하한은 안전장치다. 취향 프로필과 사실상 무관한 수준만 걸러낸다.
        when(fixture.pythonEmbeddingClient.scorePersonalProfile(
                eq(profile.positiveSignals()), eq(profile.negativeSignals()), eq(List.of(20L, 21L))))
                .thenReturn(new PythonEmbeddingClient.EmbeddingResult(
                        "KURE", "test", "test-index", 1, Map.of(20L, 0.55, 21L, 0.22)
                ));

        PersonalRecommendationResponse response = fixture.service.recommendForUser(
                ACCOUNT, LATITUDE, LONGITUDE, 3_000.0, 10
        );

        assertThat(response.items()).extracting(item -> item.restaurantId())
                .containsExactly(20L);
        verify(fixture.restaurantQualityService).scoreAll(List.of(20L));
    }

    private static PersonalPreferenceProfile profileWithPositiveRatingOnly(Long restaurantId) {
        return new PersonalPreferenceProfile(
                List.of(new WeightedRestaurantSignal(restaurantId, 1.0)),
                List.of(),
                Set.of(restaurantId),
                Set.of("일식"),
                null,
                null,
                0,
                1
        );
    }

    private static PersonalPreferenceProfile profileWithFavorite(Long restaurantId) {
        return new PersonalPreferenceProfile(
                List.of(new WeightedRestaurantSignal(restaurantId, 0.75)),
                List.of(),
                Set.of(restaurantId),
                Set.of("일식"),
                null,
                null,
                1,
                0
        );
    }

    private static PersonalPreferenceProfile profileWithFavoriteAndNegativeRating(
            Long favoriteRestaurantId,
            Long negativeRestaurantId
    ) {
        return new PersonalPreferenceProfile(
                List.of(new WeightedRestaurantSignal(favoriteRestaurantId, 0.75)),
                List.of(new WeightedRestaurantSignal(negativeRestaurantId, 1.0)),
                Set.of(favoriteRestaurantId, negativeRestaurantId),
                Set.of("일식"),
                null,
                null,
                1,
                1
        );
    }

    private static PublicRestaurant restaurant(Long id, String name, String category) {
        return restaurant(id, name, category, LATITUDE, LONGITUDE);
    }

    private static PublicRestaurant restaurant(
            Long id,
            String name,
            String category,
            double latitude,
            double longitude
    ) {
        PublicRestaurant restaurant = mock(PublicRestaurant.class);
        when(restaurant.getPublicRestaurantId()).thenReturn(id);
        when(restaurant.getName()).thenReturn(name);
        when(restaurant.getCategoryMediumName()).thenReturn(category);
        when(restaurant.getRoadAddress()).thenReturn("서울시 테스트로 " + id);
        when(restaurant.getLatitude()).thenReturn(BigDecimal.valueOf(latitude));
        when(restaurant.getLongitude()).thenReturn(BigDecimal.valueOf(longitude));
        return restaurant;
    }

    private static final class Fixture {
        private final RecommendationScoreCalculator scoreCalculator =
                mock(RecommendationScoreCalculator.class);
        private final PublicRecommendationQueryRepository publicQueryRepository =
                mock(PublicRecommendationQueryRepository.class);
        private final RecommendationDocumentService documentService =
                mock(RecommendationDocumentService.class);
        private final PythonEmbeddingClient pythonEmbeddingClient = mock(PythonEmbeddingClient.class);
        private final CandidateFilterService candidateFilterService = mock(CandidateFilterService.class);
        private final PersonalPreferenceService personalPreferenceService =
                mock(PersonalPreferenceService.class);
        private final RestaurantQualityService restaurantQualityService =
                mock(RestaurantQualityService.class);
        private final DemographicPreferenceService demographicPreferenceService =
                mock(DemographicPreferenceService.class);
        private final RecommendationService service = new RecommendationService(
                mock(RecommendationQueryParser.class),
                scoreCalculator,
                new RecommendationScoreNormalizer(),
                publicQueryRepository,
                mock(RecommendationQueryRepository.class),
                documentService,
                pythonEmbeddingClient,
                mock(KakaoLocalGeocodingClient.class),
                candidateFilterService,
                mock(EvidenceFilterService.class),
                mock(RecommendationEngineRouter.class),
                mock(ReviewRepository.class),
                mock(PublicRestaurantFavoriteQueryRepository.class),
                mock(SentimentAnalysisClient.class),
                mock(PublicRestaurantImageService.class),
                personalPreferenceService,
                restaurantQualityService,
                demographicPreferenceService
        );

        private Fixture() {
            when(demographicPreferenceService.resolve(isNull(), isNull()))
                    .thenReturn(DemographicPreferenceService.DemographicPreference.unavailable());
        }

        private void givenProfile(PersonalPreferenceProfile profile) {
            when(personalPreferenceService.build(ACCOUNT_ID)).thenReturn(profile);
        }

        private void givenCandidates(PublicRestaurant... candidates) {
            when(candidateFilterService.selectWithoutQuery(LATITUDE, LONGITUDE, 3_000))
                    .thenReturn(new CandidateFilterService.CandidateSelection(
                            List.of(candidates), List.of(), 3_000
                    ));
        }

        private void givenQuality(Long id, long reviewCount, Double averageRating, double score) {
            when(restaurantQualityService.scoreAll(List.of(id))).thenReturn(Map.of(
                    id,
                    new RestaurantQualityService.RestaurantQuality(
                            id, reviewCount, averageRating, score
                    )
            ));
        }
    }
}
