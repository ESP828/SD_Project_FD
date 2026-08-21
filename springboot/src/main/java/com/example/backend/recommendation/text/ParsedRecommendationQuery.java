package com.example.backend.recommendation.text;

import java.util.List;

/**
 * 자연어 검색어를 분석(파싱)한 결과를 담는 클래스
 */
public record ParsedRecommendationQuery(
        String originalQuery,
        String locationText,
        String locationCandidate,
        List<String> normalizedTokens,
        List<String> categoryTokens,
        List<String> purposeTokens,
        List<String> atmosphereTokens,
        List<String> priceTokens,
        boolean nearby,
        String inferredGender
) {
}
