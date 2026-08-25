package com.example.backend.recommendation.score;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RecommendationScoreNormalizer {

    /** shortlist 안에서 꼴찌가 받는 상대 점수의 하한. 0으로 두면 하위권이 통째로 죽는다. */
    private static final double SHORTLIST_RELATIVE_FLOOR = 0.5;

    /**
     * 자연어 검색의 KURE 신뢰도 구간.
     *
     * <p>이 구간은 사용자가 입력한 문장 전체가 아니라 <b>파서가 카테고리를 떼어내고 남긴
     * 잔여 텍스트</b>에 적용된다. 카테고리는 SQL 필터로 먼저 걸러지기 때문이다.
     * <pre>
     * "조용한 카페" -> semanticText "조용한"
     * "매운 국밥"   -> semanticText "매운"
     * "초밥"       -> semanticText 없음 (FILTER_ONLY)
     * </pre>
     *
     * <p>잔여 텍스트의 실측 분포는 강남 3km 후보 기준 0.29~0.57이라 아래 구간이 들어맞는다.
     * 값을 올리면 "조용한"(max 0.3848) 같은 정상 질의가 통째로 0점이 되어
     * {@code SEMANTIC_EVIDENCE_LOW}로 빠지고 결과가 거리순으로만 나온다.
     *
     * <p>참고로 절대 하한만으로 "무관한 질의"를 걸러내려는 시도는 이 경로에서 통하지 않는다.
     * 무관한 "자동차 정비"(0.4151)가 정상 질의 "조용한"(0.3848)보다 높게 나오기 때문이다.
     * 관련성 보장은 카테고리 필터가 맡는다.
     */
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

    /**
     * 개인화 전용 취향 점수. 상대 순위를 후보 전체가 아니라 shortlist 안에서만 매긴다.
     *
     * <p>후보 13,000건 전체를 percentile로 누르면 상위권 간격이 0.0001까지 좁아져
     * 품질/집단 신호가 순위에 전혀 개입하지 못한다(실측: 상위 300건의 원점수 폭은 0.06인데
     * 전체 percentile로 바꾸면 1.0000, 0.9999, 0.9999 …로 뭉개졌다).
     * shortlist 안에서 min-max로 펴면 실제 유사도 격차가 그대로 남는다.
     *
     * <p>여기에 절대 신뢰도를 곱하지는 않는다. KURE 개인화 점수의 절대 스케일은
     * 사용자마다 통째로 움직인다. {@code score_profile}이 부정 프로필 유사도를 빼기 때문에,
     * 부정 신호가 없는 사용자는 0.49~0.80, 부정 신호가 있는 사용자는 0.29~0.60처럼
     * 구간 자체가 어긋난다(실측). 고정 상한을 곱하면 후자의 취향 점수만 1/3로 눌려
     * 품질 신호가 의도보다 큰 영향을 갖게 된다. 취향과 무관한 후보를 걸러내는 일은
     * 곱셈이 아니라 절대 하한 게이트가 맡는다.
     *
     * <p>꼴찌에게도 하한을 남긴다. shortlist 안에서 꼴찌라는 것과 취향에 맞지 않는다는 것은
     * 다른 말이고, 후자는 이미 게이트가 걸러낸 뒤다.
     */
    public double shortlistTasteScore(
            double rawScore,
            double shortlistMinimum,
            double shortlistMaximum
    ) {
        double spread = shortlistMaximum - shortlistMinimum;
        double relative = spread <= 1e-9
                ? 1.0
                : clamp((rawScore - shortlistMinimum) / spread);
        return clamp(SHORTLIST_RELATIVE_FLOOR + (1.0 - SHORTLIST_RELATIVE_FLOOR) * relative);
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
