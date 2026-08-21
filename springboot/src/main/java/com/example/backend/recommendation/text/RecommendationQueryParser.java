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
            return new ParsedRecommendationQuery("", "", "", List.of(), List.of(), List.of(), List.of(), List.of(), false, null);
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
        String inferredGender = null;

        // 💡 성별 표현은 카테고리·불용어 분류와 별개로 항상 감지한다 (이 토큰이 동시에
        // 일반 카테고리 토큰으로도 쓰이는 경우가 있어 아래 메인 루프를 건드리지 않는다).
        for (String rawToken : rawTokens) {
            String token = stripPostposition(rawToken);
            if (inferredGender == null && rules.getGenderKeywords().containsKey(token)) {
                inferredGender = rules.getGenderKeywords().get(token);
            }
        }
        // 💡 접미사로 확정 짓지 못한 일반 명사 토큰 중 첫 번째를 저신뢰 지명 후보로 보관한다.
        // (예: "신논현 주변에 있는 라멘집" -> "신논현"은 역/동/구/시로 끝나지 않아 locationText로는 못 잡지만
        //  문장 맨 앞에 오는 명사이므로 지명일 가능성이 높다. 실제 지명인지는 카카오 API 응답으로 검증한다.)
        String locationCandidate = "";

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
            } else if (token.endsWith("역") || token.endsWith("동") || token.endsWith("구") || token.endsWith("시")
                    || token.endsWith("로") || token.endsWith("가")) {
                locationText = token; // 지역 키워드 감지 (고신뢰: 행정/도로명 접미사 포함)
                allNormalizedTokens.add(token);
            } else {
                if (token.length() >= 2) {
                    allNormalizedTokens.add(token);
                    // 접미사로 확정하지 못한 첫 일반 명사를 저신뢰 지명 후보로 보관
                    if (locationCandidate.isEmpty()) {
                        locationCandidate = token;
                    }
                }
            }
        }

        return new ParsedRecommendationQuery(
                trimmed,
                locationText,
                locationCandidate,
                allNormalizedTokens.stream().distinct().toList(),
                categoryTokens.stream().distinct().toList(),
                purposeTokens.stream().distinct().toList(),
                atmosphereTokens.stream().distinct().toList(),
                priceTokens.stream().distinct().toList(),
                nearby,
                inferredGender
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
