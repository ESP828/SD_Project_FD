package com.example.backend.recommendation.score;

import com.example.backend.recommendation.model.RecommendationModelStore;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 파싱된 검색어 토큰과 맛집 문서 간의 TF-IDF 코사인 유사도를 계산하는 클래스
 */
@Component
public class RecommendationScoreCalculator {

    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}\\p{N}_]+");

    private final RecommendationModelStore modelStore;

    public RecommendationScoreCalculator(RecommendationModelStore modelStore) {
        this.modelStore = modelStore;
    }

    /**
     * 검색어 토큰 리스트와 특정 맛집 텍스트 간의 코사인 유사도(0.0 ~ 1.0) 계산
     */
    public double calculateTextSimilarity(List<String> queryTokens, String restaurantDoc) {
        if (!modelStore.isAvailable() || queryTokens == null || queryTokens.isEmpty() || restaurantDoc == null) {
            return 0.0;
        }

        Map<String, Integer> vocab = modelStore.getVocabulary();
        List<Double> idfList = modelStore.getIdf();

        if (vocab.isEmpty() || idfList.isEmpty()) {
            return 0.0;
        }

        Map<Integer, Double> queryVector = vectorize(tokenize(queryTokens), vocab, idfList);
        Map<Integer, Double> documentVector =
                vectorize(tokenize(List.of(restaurantDoc)), vocab, idfList);

        double dotProduct = queryVector.entrySet().stream()
                .mapToDouble(entry -> entry.getValue() * documentVector.getOrDefault(entry.getKey(), 0.0))
                .sum();
        double queryNormSq = queryVector.values().stream()
                .mapToDouble(value -> value * value)
                .sum();
        double docNormSq = documentVector.values().stream()
                .mapToDouble(value -> value * value)
                .sum();

        if (queryNormSq == 0.0 || docNormSq == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(queryNormSq) * Math.sqrt(docNormSq));
    }

    private static Map<Integer, Double> vectorize(
            List<String> tokens,
            Map<String, Integer> vocabulary,
            List<Double> idf
    ) {
        Map<Integer, Double> vector = new HashMap<>();
        for (String token : tokens) {
            Integer index = vocabulary.get(token);
            if (index != null && index >= 0 && index < idf.size()) {
                vector.merge(index, idf.get(index), Double::sum);
            }
        }
        return vector;
    }

    private static List<String> tokenize(List<String> values) {
        List<String> tokens = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            Matcher matcher = WORD_PATTERN.matcher(value.toLowerCase(Locale.ROOT));
            while (matcher.find()) {
                tokens.add(matcher.group());
            }
        }
        return tokens;
    }
}
