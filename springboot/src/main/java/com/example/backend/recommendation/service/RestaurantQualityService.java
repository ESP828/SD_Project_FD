package com.example.backend.recommendation.service;

import com.example.backend.review.repository.PublicRestaurantReviewAggregate;
import com.example.backend.review.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FOODUCK 전체 이용자가 매긴 평점으로 매장 자체의 품질 점수를 만든다.
 *
 * <p>맛집 랭킹({@code getTopRankedRestaurants})이 쓰는 베이지안 보정을 그대로 재사용한다.
 * 리뷰 3개짜리 5점이 리뷰 30개짜리 4.5점을 이기면 안 되기 때문이다.
 * 개인화 추천은 호출마다 감성분석까지 돌릴 필요가 없으므로 평균 평점과 리뷰 수만 본다.
 */
@Service
@Transactional(readOnly = true)
public class RestaurantQualityService {

    /** 이 개수만큼 리뷰가 쌓이기 전까지는 prior 쪽으로 끌어당긴다. 랭킹과 같은 값을 쓴다. */
    public static final double BAYESIAN_MIN_REVIEWS = 8.0;

    /** 리뷰가 하나도 없는 DB에서 쓰는 기본 prior. 랭킹과 같은 값을 쓴다. */
    public static final double DEFAULT_PRIOR_RATING = 3.5;

    private static final double MAX_RATING = 5.0;

    private final ReviewRepository reviewRepository;

    public RestaurantQualityService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    /**
     * 후보 전체의 리뷰를 한 번에 집계한다(매장마다 쿼리하면 후보 1000건에서 그대로 1000번이 된다).
     * 리뷰가 없는 매장은 prior 값을 그대로 받아 중립이 되고, 신호가 빠지지는 않는다.
     */
    public Map<Long, RestaurantQuality> scoreAll(List<Long> publicRestaurantIds) {
        if (publicRestaurantIds == null || publicRestaurantIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, PublicRestaurantReviewAggregate> aggregates = new HashMap<>();
        reviewRepository.aggregateActiveByPublicRestaurantIds(publicRestaurantIds)
                .forEach(row -> aggregates.put(row.publicRestaurantId(), row));

        double priorRating = aggregates.values().stream()
                .filter(aggregate -> aggregate.averageRating() != null)
                .mapToDouble(PublicRestaurantReviewAggregate::averageRating)
                .average()
                .orElse(DEFAULT_PRIOR_RATING);

        Map<Long, RestaurantQuality> result = new HashMap<>();
        for (Long id : publicRestaurantIds) {
            PublicRestaurantReviewAggregate aggregate = aggregates.get(id);
            long reviewCount = (aggregate != null && aggregate.reviewCount() != null)
                    ? aggregate.reviewCount()
                    : 0L;
            Double averageRating = aggregate == null ? null : aggregate.averageRating();
            double rawRating = averageRating != null ? averageRating : priorRating;

            double weight = reviewCount / (reviewCount + BAYESIAN_MIN_REVIEWS);
            double bayesianRating = weight * rawRating + (1 - weight) * priorRating;

            result.put(id, new RestaurantQuality(
                    id,
                    reviewCount,
                    averageRating,
                    clamp(bayesianRating / MAX_RATING)
            ));
        }
        return result;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * @param qualityScore 0~1로 환산한 베이지안 평점. percentile로 다시 정규화하면 안 된다
     *                     (리뷰 없는 매장이 대다수라 전부 같은 값으로 뭉쳐 변별력이 사라진다).
     */
    public record RestaurantQuality(
            Long publicRestaurantId,
            long reviewCount,
            Double averageRating,
            double qualityScore
    ) {
    }
}
