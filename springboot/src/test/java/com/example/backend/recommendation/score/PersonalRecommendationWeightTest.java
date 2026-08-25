package com.example.backend.recommendation.score;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 개인화 추천의 동적 가중치를 고정한다.
 *
 * <p>{@code RecommendationService}의 개인화 가중치는 taste 0.55 / quality 0.20 /
 * demographic 0.10이고, 쓸 수 없는 신호는 null로 넘어와 자동으로 빠진다.
 * 거리는 후보 반경과 화면 표시에만 쓰며 이 계산에는 들어오지 않는다.
 * 신호가 빠졌을 때 남은 가중치끼리 다시 정규화되는지가 이 구조의 핵심이라 여기서 못박는다.
 */
class PersonalRecommendationWeightTest {

    private static final double TASTE = 0.55;
    private static final double QUALITY = 0.20;
    private static final double DEMOGRAPHIC = 0.10;

    private final RecommendationScoreNormalizer normalizer = new RecommendationScoreNormalizer();

    @Test
    void usesTasteQualityAndDemographicWhenEverySignalIsAvailable() {
        double score = normalizer.weightedMean(
                RecommendationScoreNormalizer.signal(1.0, TASTE),
                RecommendationScoreNormalizer.signal(0.0, QUALITY),
                RecommendationScoreNormalizer.signal(0.0, DEMOGRAPHIC)
        );

        assertThat(score).isEqualTo(0.55 / 0.85, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void redistributesWeightWhenDemographicIsUnavailable() {
        // 나이/성별 표본이 부족하면 0.10이 사라지는 것이 아니라 나머지 0.75로 다시 나뉜다.
        double score = normalizer.weightedMean(
                RecommendationScoreNormalizer.signal(1.0, TASTE),
                RecommendationScoreNormalizer.signal(0.0, QUALITY),
                RecommendationScoreNormalizer.signal(null, DEMOGRAPHIC)
        );

        assertThat(score).isEqualTo(0.55 / 0.75, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void tasteOutweighsQualityAndDemographicSignals() {
        double a = normalizer.weightedMean(
                RecommendationScoreNormalizer.signal(0.92, TASTE),
                RecommendationScoreNormalizer.signal(0.86, QUALITY),
                RecommendationScoreNormalizer.signal(0.70, DEMOGRAPHIC)
        );
        double b = normalizer.weightedMean(
                RecommendationScoreNormalizer.signal(0.31, TASTE),
                RecommendationScoreNormalizer.signal(0.95, QUALITY),
                RecommendationScoreNormalizer.signal(0.72, DEMOGRAPHIC)
        );

        assertThat(a).isGreaterThan(b);
    }

    @Test
    void demographicAloneCannotOutrankDirectBehaviour() {
        // 나이/성별만 최고점인 후보가, 내 행동과 잘 맞는 후보를 이기면 안 된다.
        double behaviour = normalizer.weightedMean(
                RecommendationScoreNormalizer.signal(0.90, TASTE),
                RecommendationScoreNormalizer.signal(0.50, QUALITY),
                RecommendationScoreNormalizer.signal(0.00, DEMOGRAPHIC)
        );
        double demographicOnly = normalizer.weightedMean(
                RecommendationScoreNormalizer.signal(0.20, TASTE),
                RecommendationScoreNormalizer.signal(0.50, QUALITY),
                RecommendationScoreNormalizer.signal(1.00, DEMOGRAPHIC)
        );

        assertThat(behaviour).isGreaterThan(demographicOnly);
    }
}
