package com.example.backend.recommendation.preference;

import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.recommendation.preference.domain.PersonalPreferenceProfile;
import com.example.backend.recommendation.preference.domain.WeightedRestaurantSignal;
import com.example.backend.recommendation.query.RecommendationQueryRepository;
import com.example.backend.recommendation.query.RecommendationQueryRepository.RestaurantCandidate;
import com.example.backend.review.repository.AccountPublicRestaurantRating;
import com.example.backend.review.repository.ReviewRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersonalPreferenceServiceTest {

    private static final long ACCOUNT_ID = 7L;

    @Test
    void keepsFavoritesDominantAndUsesRatingsAsSeparateAdjustments() {
        PersonalPreferenceProfile profile = build(
                List.of(100L),
                List.of(rating(100L, 5), rating(200L, 5), rating(300L, 4))
        );

        Map<Long, Double> positive = byId(profile.positiveSignals());

        assertThat(positive).containsOnlyKeys(100L, 200L, 300L);
        assertThat(positive.get(100L)).isEqualTo(0.75, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(positive.get(200L) + positive.get(300L))
                .isEqualTo(0.25, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(profile.negativeSignals()).isEmpty();
        assertThat(profile.preferredCategories()).containsExactly("한식");
    }

    @Test
    void movesAFavoriteToTheNegativeProfileWhenIRatedItBadly() {
        PersonalPreferenceProfile profile = build(
                List.of(100L, 400L),
                List.of(rating(100L, 1), rating(400L, 5))
        );

        Map<Long, Double> positive = byId(profile.positiveSignals());
        Map<Long, Double> negative = byId(profile.negativeSignals());

        // 찜 + 1점 = 0.7 - 1.0 = -0.3. 같은 매장이 긍정과 부정에 동시에 들어가면 두 프로필이 서로를 상쇄한다.
        assertThat(positive).containsOnlyKeys(400L);
        assertThat(positive.get(400L)).isEqualTo(0.75, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(negative).containsOnlyKeys(100L);
        assertThat(negative.get(100L)).isEqualTo(0.3, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void doesNotCountAFavoriteRatingTwiceAcrossTheTwoPositiveGroups() {
        PersonalPreferenceProfile favoritedAndLoved = build(
                List.of(100L), List.of(rating(100L, 5))
        );
        PersonalPreferenceProfile favoritedAndTolerated = build(
                List.of(100L), List.of(rating(100L, 2))
        );

        // 찜한 매장의 평점은 찜 그룹 안에서 한 번만 반영된다(찜+5점 1.7, 찜+2점 0.1).
        assertThat(byId(favoritedAndLoved.positiveSignals())).containsOnlyKeys(100L);
        assertThat(byId(favoritedAndTolerated.positiveSignals())).containsOnlyKeys(100L);
        assertThat(favoritedAndTolerated.negativeSignals()).isEmpty();
    }

    @Test
    void treatsThreeStarAsNeutralButStillExperienced() {
        PersonalPreferenceProfile profile = build(List.of(), List.of(rating(500L, 3)));

        assertThat(profile.positiveSignals()).isEmpty();
        assertThat(profile.negativeSignals()).isEmpty();
        assertThat(profile.experiencedRestaurantIds()).containsExactly(500L);
    }

    @Test
    void keepsFavouriteAndReviewedRestaurantsOutOfNewRecommendations() {
        PersonalPreferenceProfile profile = build(
                List.of(100L),
                List.of(rating(200L, 2), rating(300L, 3))
        );

        assertThat(profile.experiencedRestaurantIds())
                .containsExactlyInAnyOrder(100L, 200L, 300L);
    }

    @Test
    void requiresFavoritesWhileTreatingRatingsAndProfileAsOptionalSignals() {
        assertThat(build(List.of(100L), List.of()).personalizationLevel())
                .isEqualTo("BEHAVIOR_ONLY");
        assertThat(build(List.of(), List.of()).personalizationLevel())
                .isEqualTo("NO_FAVORITES");
        assertThat(build(List.of(), List.of(rating(200L, 5))).personalizationLevel())
                .isEqualTo("NO_FAVORITES");
        assertThat(build(List.of(), List.of(rating(200L, 1))).personalizationLevel())
                .isEqualTo("NO_FAVORITES");

        // 찜한 매장이 하나뿐인데 그마저 1점이면 긍정 취향 벡터를 만들 재료가 없다.
        // KURE는 긍정 프로필이 비면 KURE_PROFILE_NOT_READY로 거절하므로, 찜 카테고리 기반
        // TF-IDF로 내려가 페이지를 살린다. 찜은 있으므로 개인화 수준 자체는 유지된다.
        PersonalPreferenceProfile dislikedFavorite = build(
                List.of(300L), List.of(rating(300L, 1))
        );
        assertThat(dislikedFavorite.hasTasteSignal()).isFalse();
        assertThat(dislikedFavorite.preferredCategories()).containsExactly("한식");
        assertThat(dislikedFavorite.personalizationLevel()).isEqualTo("BEHAVIOR_ONLY");
    }

    @Test
    void userWithoutFavoritesGetsGuidanceInsteadOfGenericNearbyRecommendation() {
        PersonalPreferenceProfile profile = build(List.of(), List.of());

        assertThat(profile.hasFavorites()).isFalse();
        assertThat(PersonalPreferenceService.preferenceSummary(profile))
                .contains("찜한 맛집");
    }

    @Test
    void ratingWeightsFollowTheHandoverTable() {
        assertThat(PersonalPreferenceService.ratingWeight(5)).isEqualTo(1.0);
        assertThat(PersonalPreferenceService.ratingWeight(4)).isEqualTo(0.6);
        assertThat(PersonalPreferenceService.ratingWeight(3)).isEqualTo(0.0);
        assertThat(PersonalPreferenceService.ratingWeight(2)).isEqualTo(-0.6);
        assertThat(PersonalPreferenceService.ratingWeight(1)).isEqualTo(-1.0);
    }

    @Test
    void keepsEveryExperiencedRestaurantEvenWhenKureSignalsAreCapped() {
        List<Long> favoriteIds = LongStream.rangeClosed(1, 501).boxed().toList();

        PersonalPreferenceProfile profile = build(favoriteIds, List.of());

        assertThat(profile.experiencedRestaurantIds()).hasSize(501);
        assertThat(profile.positiveSignals()).hasSize(500);
    }

    @Test
    void groupsEveryAgeFromSixtyUpIntoOneCohort() {
        assertThat(PersonalPreferenceService.normalizeAgeGroup(59)).isEqualTo(50);
        assertThat(PersonalPreferenceService.normalizeAgeGroup(60)).isEqualTo(60);
        assertThat(PersonalPreferenceService.normalizeAgeGroup(87)).isEqualTo(60);
    }

    private static PersonalPreferenceProfile build(
            List<Long> publicFavoriteIds,
            List<AccountPublicRestaurantRating> ratings
    ) {
        AccountRepository accountRepository = mock(AccountRepository.class);
        RecommendationQueryRepository queryRepository = mock(RecommendationQueryRepository.class);
        ReviewRepository reviewRepository = mock(ReviewRepository.class);
        when(accountRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(queryRepository.findFavoritesByAccountId(ACCOUNT_ID)).thenReturn(favoriteRows(publicFavoriteIds));
        when(queryRepository.findPublicFavoriteIdsByAccountId(ACCOUNT_ID)).thenReturn(publicFavoriteIds);
        when(reviewRepository.findActivePublicRatingsByAccountId(ACCOUNT_ID)).thenReturn(ratings);

        return new PersonalPreferenceService(
                accountRepository, queryRepository, reviewRepository
        ).build(ACCOUNT_ID);
    }

    private static List<RestaurantCandidate> favoriteRows(List<Long> ids) {
        return ids.stream()
                .map(id -> new RestaurantCandidate(
                        id, "가게" + id, "한식", "주소", "", null, null,
                        "", "", null, "", 0.0, 0L, 1L, true, 1.0
                ))
                .toList();
    }

    private static AccountPublicRestaurantRating rating(long restaurantId, int rating) {
        return new AccountPublicRestaurantRating(restaurantId, (byte) rating);
    }

    private static Map<Long, Double> byId(List<WeightedRestaurantSignal> signals) {
        return signals.stream().collect(Collectors.toMap(
                WeightedRestaurantSignal::restaurantId,
                WeightedRestaurantSignal::weight,
                (left, right) -> left,
                java.util.LinkedHashMap::new
        ));
    }

}
