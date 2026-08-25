package com.example.backend.recommendation.score;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationScoreNormalizerTest {

    private final RecommendationScoreNormalizer normalizer = new RecommendationScoreNormalizer();

    @Test
    void percentileRanksHandleTiesDeterministically() {
        Map<Long, Double> rawScores = new LinkedHashMap<>();
        rawScores.put(10L, 0.1);
        rawScores.put(20L, 0.5);
        rawScores.put(30L, 0.5);
        rawScores.put(40L, 0.9);

        Map<Long, Double> normalized = normalizer.percentileRanks(rawScores);

        assertThat(normalized.get(10L)).isEqualTo(0.0);
        assertThat(normalized.get(20L)).isEqualTo(0.5);
        assertThat(normalized.get(30L)).isEqualTo(0.5);
        assertThat(normalized.get(40L)).isEqualTo(1.0);
    }

    @Test
    void weightedMeanExcludesMissingSignals() {
        double result = normalizer.weightedMean(
                RecommendationScoreNormalizer.signal(0.8, 0.75),
                RecommendationScoreNormalizer.signal(null, 0.25)
        );

        assertThat(result).isCloseTo(0.8, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void confidenceAdjustedRankSuppressesWeakRelativeWinner() {
        // KURE 구간 0.20~0.55는 파서가 카테고리를 떼고 남긴 잔여 텍스트에 맞춘 값이다.
        // "조용한"(0.3848) 같은 정상 질의가 살아 있어야 하므로 하한을 올리면 안 된다.
        assertThat(normalizer.confidenceAdjustedRank("KURE", 0.20, 1.0)).isZero();
        assertThat(normalizer.confidenceAdjustedRank("KURE", 0.375, 1.0))
                .isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(normalizer.confidenceAdjustedRank("KURE", 0.55, 1.0)).isEqualTo(1.0);
    }

    @Test
    void confidenceAdjustedRankUsesEngineSpecificScale() {
        assertThat(normalizer.confidenceAdjustedRank("TFIDF", 0.125, 0.8))
                .isCloseTo(0.4, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(normalizer.confidenceAdjustedRank("FILTER_ONLY", 1.0, 1.0)).isNull();
    }
}
