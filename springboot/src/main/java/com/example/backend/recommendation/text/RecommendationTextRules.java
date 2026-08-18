package com.example.backend.recommendation.text;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RecommendationTextRules {

    private final Set<String> nearbyKeywords = new HashSet<>();
    private final Set<String> purposeKeywords = new HashSet<>();
    private final Set<String> atmosphereKeywords = new HashSet<>();
    private final Set<String> priceKeywords = new HashSet<>();
    private final Set<String> stopwords = new HashSet<>();
    private final Map<String, List<String>> synonyms = new HashMap<>();
    private final List<String> postpositions = new ArrayList<>();

    public RecommendationTextRules() {
        initRules();
    }

    private void initRules() {
        // 1. 위치/주변 키워드
        nearbyKeywords.addAll(Arrays.asList("근처", "주변", "인근", "가까운", "주변에", "근처에", "여기", "내위치", "현위치"));

        // 2. 목적 키워드 (Set<String>)
        purposeKeywords.addAll(Arrays.asList("데이트", "회식", "모임", "혼밥", "가족", "술자리", "술", "해장"));

        // 3. 분위기 키워드 (Set<String>)
        atmosphereKeywords.addAll(Arrays.asList("조용한", "분위기", "감성", "힙한", "가성비", "고급", "깔끔한", "노포"));

        // 4. 가격대 키워드 (Set<String>)
        priceKeywords.addAll(Arrays.asList("저렴한", "싼", "가성비", "비싼", "고급"));

        // 5. 불용어 (단, '전', '면' 등 음식 핵심 1글자는 제외)
        stopwords.addAll(Arrays.asList("맛집", "추천", "알려줘", "어디", "가볼만한곳", "식당", "음식점", "맛있는", "잘하는", "괜찮은", "있는"));

        // 6. 동의어 매핑 (Map<String, List<String>>) - 정답지 연관 단어 매핑
        synonyms.put("전", List.of("파전", "해물파전", "김치전", "감자전", "녹두전", "빈대떡", "부침개", "지짐이", "주막", "막걸리", "민속주점"));
        synonyms.put("파전", List.of("해물파전", "전", "빈대떡", "부침개", "민속주점", "막걸리"));
        synonyms.put("비", List.of("비오는날", "파전", "김치전", "수제비", "칼국수", "막걸리", "전"));
        synonyms.put("면", List.of("칼국수", "라멘", "우동", "짜장면", "짬뽕", "파스타", "소바", "냉면", "국수"));
        synonyms.put("해장", List.of("해장국", "국밥", "순대국", "뼈해장국", "황태해장국", "콩나물국밥", "라면", "짬뽕"));
        synonyms.put("고기", List.of("삼겹살", "돼지갈비", "소고기", "한우", "구이", "생고기", "목살"));
        synonyms.put("삼겹살", List.of("고기", "돼지고기", "구이"));
        synonyms.put("파스타", List.of("양식", "스파게티", "이탈리안"));
        synonyms.put("초밥", List.of("일식", "스시", "회"));
        synonyms.put("짜장면", List.of("중식", "중화요리", "중국집"));

        // 7. 한국어 조사
        postpositions.addAll(Arrays.asList("에서", "으로", "로는", "에는", "까지", "부터", "은", "는", "이", "가", "을", "를", "에", "와", "과", "도", "로"));
    }

    public Set<String> getNearbyKeywords() {
        return nearbyKeywords;
    }

    public Set<String> getPurposeKeywords() {
        return purposeKeywords;
    }

    public Set<String> getAtmosphereKeywords() {
        return atmosphereKeywords;
    }

    public Set<String> getPriceKeywords() {
        return priceKeywords;
    }

    public Set<String> getStopwords() {
        return stopwords;
    }

    public Map<String, List<String>> getSynonyms() {
        return synonyms;
    }

    public List<String> getPostpositions() {
        return postpositions;
    }
}
