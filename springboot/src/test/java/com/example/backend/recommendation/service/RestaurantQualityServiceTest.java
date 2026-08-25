package com.example.backend.recommendation.service;

import com.example.backend.recommendation.service.RestaurantQualityService.RestaurantQuality;
import com.example.backend.review.repository.PublicRestaurantReviewAggregate;
import com.example.backend.review.repository.ReviewRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestaurantQualityServiceTest {

    @Test
    void bayesianCorrectionKeepsThinFiveStarBelowAWellReviewedFourStar() {
        // prior는 리뷰가 있는 매장들의 평균 평점을 단순 평균한 값이다(맛집 랭킹과 같은 계산).
        // 그래서 표본이 실제 DB처럼 여러 매장으로 퍼져 있어야 보정이 의미를 갖는다.
        Map<Long, RestaurantQuality> scores = scoreAll(
                List.of(10L, 20L, 31L, 32L, 33L, 34L),
                aggregate(10L, 2L, 5.0),
                aggregate(20L, 40L, 4.5),
                aggregate(31L, 20L, 3.0),
                aggregate(32L, 20L, 3.2),
                aggregate(33L, 20L, 3.4),
                aggregate(34L, 20L, 3.5)
        );

        assertThat(scores.get(10L).qualityScore())
                .isLessThan(scores.get(20L).qualityScore());
    }

    @Test
    void restaurantsWithoutReviewsFallBackToThePriorInsteadOfDroppingOut() {
        Map<Long, RestaurantQuality> scores = scoreAll(
                List.of(10L, 99L),
                aggregate(10L, 40L, 4.0)
        );

        RestaurantQuality unreviewed = scores.get(99L);
        assertThat(unreviewed.reviewCount()).isZero();
        assertThat(unreviewed.averageRating()).isNull();
        // prior(= 리뷰가 있는 매장들의 평균 4.0)를 그대로 받아 중립이 된다.
        assertThat(unreviewed.qualityScore()).isEqualTo(4.0 / 5.0);
    }

    @Test
    void lowRatedRestaurantsScoreBelowNeutral() {
        Map<Long, RestaurantQuality> scores = scoreAll(
                List.of(10L, 20L),
                aggregate(10L, 40L, 1.5),
                aggregate(20L, 40L, 4.5)
        );

        assertThat(scores.get(10L).qualityScore()).isLessThan(scores.get(20L).qualityScore());
        assertThat(scores.get(10L).qualityScore()).isBetween(0.0, 1.0);
    }

    @Test
    void returnsEmptyMapWhenThereAreNoCandidates() {
        ReviewRepository reviewRepository = mock(ReviewRepository.class);
        assertThat(new RestaurantQualityService(reviewRepository).scoreAll(List.of())).isEmpty();
    }

    private static Map<Long, RestaurantQuality> scoreAll(
            List<Long> candidateIds,
            PublicRestaurantReviewAggregate... aggregates
    ) {
        ReviewRepository reviewRepository = mock(ReviewRepository.class);
        when(reviewRepository.aggregateActiveByPublicRestaurantIds(anyList()))
                .thenReturn(List.of(aggregates));
        return new RestaurantQualityService(reviewRepository).scoreAll(candidateIds);
    }

    private static PublicRestaurantReviewAggregate aggregate(Long id, Long count, Double average) {
        return new PublicRestaurantReviewAggregate(id, count, average);
    }
}
