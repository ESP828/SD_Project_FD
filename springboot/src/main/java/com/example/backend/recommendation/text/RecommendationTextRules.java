package com.example.backend.recommendation.text;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

/**
 * text-rules.json 정규화 규칙을 읽어와 메모리에 로딩하는 클래스
 */
@Component
public class RecommendationTextRules {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private List<String> stopwords = Collections.emptyList();
    private List<String> postpositions = Collections.emptyList();
    private Map<String, List<String>> synonyms = Collections.emptyMap();
    private List<String> nearbyKeywords = Collections.emptyList();
    private List<String> purposeKeywords = Collections.emptyList();
    private List<String> atmosphereKeywords = Collections.emptyList();
    private List<String> priceKeywords = Collections.emptyList();

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("recommendation/text-rules.json");
            try (InputStream is = resource.getInputStream()) {
                Map<String, Object> data = objectMapper.readValue(is, Map.class);

                this.stopwords = (List<String>) data.getOrDefault("stopwords", Collections.emptyList());
                this.postpositions = (List<String>) data.getOrDefault("postpositions", Collections.emptyList());
                this.synonyms = (Map<String, List<String>>) data.getOrDefault("synonyms", Collections.emptyMap());
                this.nearbyKeywords = (List<String>) data.getOrDefault("nearbyKeywords", Collections.emptyList());
                this.purposeKeywords = (List<String>) data.getOrDefault("purposeKeywords", Collections.emptyList());
                this.atmosphereKeywords = (List<String>) data.getOrDefault("atmosphereKeywords", Collections.emptyList());
                this.priceKeywords = (List<String>) data.getOrDefault("priceKeywords", Collections.emptyList());

                // 조사는 긴 단어가 먼저 제거되도록 길이 내림차순 정렬
                this.postpositions.sort((a, b) -> Integer.compare(b.length(), a.length()));
            }
        } catch (Exception e) {
            throw new IllegalStateException("recommendation/text-rules.json 파일을 읽을 수 없습니다.", e);
        }
    }

    public List<String> getStopwords() { return stopwords; }
    public List<String> getPostpositions() { return postpositions; }
    public Map<String, List<String>> getSynonyms() { return synonyms; }
    public List<String> getNearbyKeywords() { return nearbyKeywords; }
    public List<String> getPurposeKeywords() { return purposeKeywords; }
    public List<String> getAtmosphereKeywords() { return atmosphereKeywords; }
    public List<String> getPriceKeywords() { return priceKeywords; }
}
