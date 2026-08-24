package com.example.backend.recommendation.score;

import com.example.backend.recommendation.model.RecommendationModelStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationScoreCalculatorTest {

    private RecommendationModelStore modelStore;
    private RecommendationScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        // 1. 모델 스토어 생성 및 로딩
        modelStore = new RecommendationModelStore();
        modelStore.loadModel(); // @PostConstruct 직접 호출

        // 2. 점수 계산기 생성
        calculator = new RecommendationScoreCalculator(modelStore);
    }

    @Test
    @DisplayName("TF-IDF 코사인 유사도 계산 검증")
    void calculateTextSimilarity() {
        // given
        // 검색어 토큰 (예: 파스타, 조용한)
        List<String> queryTokens = List.of("파스타", "이탈리안", "양식");

        // 맛집 1: 파스타/이탈리안 관련 맛집
        String pastaRestaurantDoc = "강남파스타 음식 양식 서양식 이탈리안 서울특별시 강남구 역삼동 123";

        // 맛집 2: 일식/초밥 관련 맛집
        String sushiRestaurantDoc = "스시마스투 음식 일식 초밥 서울특별시 강남구 역삼동 456";

        // when
        double pastaScore = calculator.calculateTextSimilarity(queryTokens, pastaRestaurantDoc);
        double sushiScore = calculator.calculateTextSimilarity(queryTokens, sushiRestaurantDoc);

        // 콘솔 출력 확인
        System.out.println("파스타집 유사도 점수: " + pastaScore);
        System.out.println("초밥집 유사도 점수: " + sushiScore);

        // then
        assertThat(modelStore.isAvailable()).isTrue();
        assertThat(pastaScore).isGreaterThan(sushiScore); // 파스타집 점수가 초밥집보다 높아야 함
    }
}
