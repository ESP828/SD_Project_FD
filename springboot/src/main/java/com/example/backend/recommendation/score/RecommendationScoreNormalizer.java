package com.example.backend.recommendation.score;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RecommendationScoreNormalizer {

    @Value("${recommendation.semantic.kure-floor:0.20}")
    private double kureFloor = 0.20;

    @Value("${recommendation.semantic.kure-ceiling:0.55}")
    private double kureCeiling = 0.55;

    @Value("${recommendation.semantic.tfidf-floor:0.0}")
    private double tfidfFloor = 0.0;

    @Value("${recommendation.semantic.tfidf-ceiling:0.25}")
    private double tfidfCeiling = 0.25;

    public Map<Long, Double> percentileRanks(Map<Long, Double> rawScores) {
        if (rawScores == null || rawScores.isEmpty()) {
            return Map.of();
        }

        List<Map.Entry<Long, Double>> sorted = new ArrayList<>(rawScores.entrySet());
        sorted.sort(Map.Entry.<Long, Double>comparingByValue()
                .thenComparing(Map.Entry.comparingByKey()));
        if (sorted.size() == 1) {
            return Map.of(sorted.get(0).getKey(), 0.5);
        }

        double minimum = sorted.get(0).getValue();
        double maximum = sorted.get(sorted.size() - 1).getValue();
        if (Math.abs(maximum - minimum) < 1e-9) {
            Map<Long, Double> neutral = new LinkedHashMap<>();
            rawScores.keySet().forEach(id -> neutral.put(id, 0.5));
            return neutral;
        }

        Map<Long, Double> normalized = new LinkedHashMap<>();
        int index = 0;
        while (index < sorted.size()) {
            int end = index;
            double score = sorted.get(index).getValue();
            while (end + 1 < sorted.size()
                    && Double.compare(sorted.get(end + 1).getValue(), score) == 0) {
                end++;
            }
            double averageRank = (index + end) / 2.0;
            double percentile = averageRank / (sorted.size() - 1.0);
            for (int position = index; position <= end; position++) {
                normalized.put(sorted.get(position).getKey(), percentile);
            }
            index = end + 1;
        }
        return normalized;
    }

    public double weightedMean(Signal... signals) {
        double weightedTotal = 0.0;
        double weightTotal = 0.0;
        for (Signal signal : signals) {
            if (signal == null || signal.value() == null || signal.weight() <= 0.0) {
                continue;
            }
            weightedTotal += clamp(signal.value()) * signal.weight();
            weightTotal += signal.weight();
        }
        return weightTotal == 0.0 ? 0.0 : clamp(weightedTotal / weightTotal);
    }

    /**
     * Percentiles are relative and always produce a winner. Multiplying them by an
     * absolute-score confidence prevents a weak candidate set from looking certain.
     */
    public Double confidenceAdjustedRank(
            String engineName,
            Double rawScore,
            Double percentileRank
    ) {
        if (rawScore == null || percentileRank == null) {
            return null;
        }
        Double floor = confidenceFloor(engineName);
        Double ceiling = confidenceCeiling(engineName);
        if (floor == null || ceiling == null) {
            return null;
        }
        if (ceiling <= floor) {
            return null;
        }
        double absoluteConfidence = clamp((rawScore - floor) / (ceiling - floor));
        return clamp(percentileRank * absoluteConfidence);
    }

    public Double confidenceFloor(String engineName) {
        if ("KURE".equalsIgnoreCase(engineName)) {
            return kureFloor;
        }
        if ("TFIDF".equalsIgnoreCase(engineName)) {
            return tfidfFloor;
        }
        return null;
    }

    public Double confidenceCeiling(String engineName) {
        if ("KURE".equalsIgnoreCase(engineName)) {
            return kureCeiling;
        }
        if ("TFIDF".equalsIgnoreCase(engineName)) {
            return tfidfCeiling;
        }
        return null;
    }

    public static Signal signal(Double value, double weight) {
        return value == null ? null : new Signal(value, weight);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record Signal(Double value, double weight) {
    }
}
