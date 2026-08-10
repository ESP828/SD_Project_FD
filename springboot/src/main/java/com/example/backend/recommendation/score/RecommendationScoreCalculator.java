package com.example.backend.recommendation.score;

import com.example.backend.recommendation.model.RecommendationModelStore;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 파싱된 검색어 토큰과 맛집 문서 간의 TF-IDF 코사인 유사도를 계산하는 클래스
 */
@Component
public class RecommendationScoreCalculator {

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

        int vocabSize = vocab.size();

        // 1. Query TF-IDF 벡터 생성
        double[] queryVector = new double[vocabSize];
        for (String token : queryTokens) {
            if (vocab.containsKey(token)) {
                int index = vocab.get(token);
                double idf = idfList.get(index);
                queryVector[index] += 1.0 * idf; // TF * IDF
            }
        }

        // 2. Restaurant Doc TF-IDF 벡터 생성
        double[] docVector = new double[vocabSize];
        String[] docTokens = restaurantDoc.split("\\s+");
        for (String token : docTokens) {
            if (vocab.containsKey(token)) {
                int index = vocab.get(token);
                double idf = idfList.get(index);
                docVector[index] += 1.0 * idf;
            }
        }

        // 3. 코사인 유사도 계산 (Cosine Similarity = dot_product / (norm(A) * norm(B)))
        double dotProduct = 0.0;
        double queryNormSq = 0.0;
        double docNormSq = 0.0;

        for (int i = 0; i < vocabSize; i++) {
            dotProduct += queryVector[i] * docVector[i];
            queryNormSq += queryVector[i] * queryVector[i];
            docNormSq += docVector[i] * docVector[i];
        }

        if (queryNormSq == 0.0 || docNormSq == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(queryNormSq) * Math.sqrt(docNormSq));
    }
}
