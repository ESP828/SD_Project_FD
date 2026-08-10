package com.example.backend.recommendation.text;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * "강남역 근처 조용한 파스타집 추천해줘" 문장을 단어별로 나누고 키워드를 분석하는 파서
 */
@Component
public class RecommendationQueryParser {

    private final RecommendationTextRules rules;

    public RecommendationQueryParser(RecommendationTextRules rules) {
        this.rules = rules;
    }

    public ParsedRecommendationQuery parse(String query) {
        if (query == null || query.isBlank()) {
            return new ParsedRecommendationQuery("", "", List.of(), List.of(), List.of(), List.of(), List.of(), false);
        }

        String trimmed = query.trim();
        String[] rawTokens = trimmed.split("\\s+");

        List<String> categoryTokens = new ArrayList<>();
        List<String> purposeTokens = new ArrayList<>();
        List<String> atmosphereTokens = new ArrayList<>();
        List<String> priceTokens = new ArrayList<>();
        List<String> allNormalizedTokens = new ArrayList<>();

        boolean nearby = false;
        String locationText = "";

        for (String rawToken : rawTokens) {
            String token = stripPostposition(rawToken);

            if (rules.getNearbyKeywords().contains(token)) {
                nearby = true;
                continue;
            }

            if (rules.getPurposeKeywords().contains(token)) {
                purposeTokens.add(token);
                allNormalizedTokens.add(token);
                continue;
            }

            if (rules.getAtmosphereKeywords().contains(token)) {
                atmosphereTokens.add(token);
                allNormalizedTokens.add(token);
                continue;
            }

            if (rules.getPriceKeywords().contains(token)) {
                priceTokens.add(token);
                allNormalizedTokens.add(token);
                continue;
            }

            if (rules.getStopwords().contains(token)) {
                continue;
            }

            // 동의어 변환 (예: 파스타집 -> 파스타, 이탈리안, 양식)
            if (rules.getSynonyms().containsKey(token)) {
                List<String> syns = rules.getSynonyms().get(token);
                categoryTokens.addAll(syns);
                allNormalizedTokens.addAll(syns);
            } else if (token.endsWith("역") || token.endsWith("동") || token.endsWith("구") || token.endsWith("시")) {
                locationText = token; // 지역 키워드 감지
                allNormalizedTokens.add(token);
            } else {
                if (token.length() >= 2) {
                    allNormalizedTokens.add(token);
                }
            }
        }

        return new ParsedRecommendationQuery(
                trimmed,
                locationText,
                allNormalizedTokens.stream().distinct().toList(),
                categoryTokens.stream().distinct().toList(),
                purposeTokens.stream().distinct().toList(),
                atmosphereTokens.stream().distinct().toList(),
                priceTokens.stream().distinct().toList(),
                nearby
        );
    }

    private String stripPostposition(String token) {
        for (String post : rules.getPostpositions()) {
            if (token.endsWith(post) && token.length() > post.length()) {
                return token.substring(0, token.length() - post.length());
            }
        }
        return token;
    }
}
