package com.example.backend.recommendation.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Spring Boot 실행 시 resources/recommendation/model/ 하위의
 * 모델 파일들(vocabulary, idf, metadata)을 읽어 메모리에 올리는 클래스
 */
@Component
public class RecommendationModelStore {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, Integer> vocabulary = Collections.emptyMap();
    private List<Double> idf = Collections.emptyList();
    private Map<String, Object> metadata = Collections.emptyMap();
    private boolean available = false;

    @PostConstruct
    public void loadModel() {
        try {
            // 1. vocabulary.json 읽기
            ClassPathResource vocabResource = new ClassPathResource("recommendation/model/vocabulary.json");
            try (InputStream is = vocabResource.getInputStream()) {
                this.vocabulary = objectMapper.readValue(is, new TypeReference<Map<String, Integer>>() {});
            }

            // 2. idf.json 읽기
            ClassPathResource idfResource = new ClassPathResource("recommendation/model/idf.json");
            try (InputStream is = idfResource.getInputStream()) {
                this.idf = objectMapper.readValue(is, new TypeReference<List<Double>>() {});
            }

            // 3. model-meta.json 읽기
            ClassPathResource metaResource = new ClassPathResource("recommendation/model/model-meta.json");
            try (InputStream is = metaResource.getInputStream()) {
                this.metadata = objectMapper.readValue(is, new TypeReference<Map<String, Object>>() {});
            }

            this.available = true;
            System.out.println("=== [RecommendationModelStore] TF-IDF 모델 로딩 성공 ===");
            System.out.println("- 단어 사전 크기: " + vocabulary.size() + "개");
            System.out.println("- 모델 버전: " + metadata.getOrDefault("modelVersion", "unknown"));

        } catch (Exception e) {
            System.err.println("=== [RecommendationModelStore] 모델 로딩 실패 (Fallback 모드로 동작합니다) ===");
            System.err.println("원인: " + e.getMessage());
            this.available = false;
        }
    }

    public boolean isAvailable() { return available; }
    public Map<String, Integer> getVocabulary() { return vocabulary; }
    public List<Double> getIdf() { return idf; }
    public Map<String, Object> getMetadata() { return metadata; }
}
