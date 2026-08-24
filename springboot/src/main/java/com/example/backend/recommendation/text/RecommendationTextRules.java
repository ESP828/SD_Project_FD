package com.example.backend.recommendation.text;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RecommendationTextRules {

    private final Set<String> nearbyKeywords = new LinkedHashSet<>();
    private final Set<String> purposeKeywords = new LinkedHashSet<>();
    private final Set<String> atmosphereKeywords = new LinkedHashSet<>();
    private final Set<String> priceKeywords = new LinkedHashSet<>();
    private final Set<String> stopwords = new LinkedHashSet<>();
    private final Map<String, List<String>> synonyms = new LinkedHashMap<>();
    private final List<String> postpositions = new ArrayList<>();

    public RecommendationTextRules() {
        initRules();
    }

    private void initRules() {
        nearbyKeywords.addAll(List.of(
                "근처", "주변", "인근", "가까운", "가까이", "부근", "근방",
                "주변에", "근처에", "여기", "내위치", "현위치"
        ));

        purposeKeywords.addAll(List.of(
                "데이트", "회식", "모임", "가족모임", "혼밥", "가족", "아이동반",
                "술자리", "술", "해장", "점심", "저녁", "소개팅"
        ));

        atmosphereKeywords.addAll(List.of(
                "조용한", "아늑한", "분위기", "감성", "대화", "사진", "힙한",
                "가성비", "고급", "깔끔한", "노포", "한옥", "야경"
        ));

        priceKeywords.addAll(List.of("저렴한", "싼", "가성비", "비싼", "고급", "만원대"));

        stopwords.addAll(List.of(
                "맛집", "추천", "추천해줘", "추천해주세요", "해주세요", "해줘", "찾아줘", "알려줘",
                "어디", "곳", "데", "가볼만한곳", "가볼만한", "식당", "음식점", "밥집",
                "맛있는", "잘하는", "괜찮은", "있는", "있어", "먹을", "먹기", "가기", "갈",
                "수", "장소", "주변에서", "근처에서", "부근"
        ));

        synonyms.put("전", List.of("파전", "해물파전", "김치전", "감자전", "녹두전", "빈대떡", "부침개", "막걸리"));
        synonyms.put("파전", List.of("해물파전", "전", "빈대떡", "부침개", "민속주점", "막걸리"));
        synonyms.put("비", List.of("비오는날", "파전", "김치전", "수제비", "칼국수", "막걸리", "전"));
        synonyms.put("면", List.of("칼국수", "라멘", "우동", "짜장면", "짬뽕", "파스타", "소바", "냉면", "국수"));
        synonyms.put("해장", List.of("해장국", "국밥", "순대국", "뼈해장국", "황태해장국", "콩나물국밥", "라면", "짬뽕"));
        synonyms.put("고기", List.of("삼겹살", "돼지갈비", "소고기", "한우", "구이", "생고기", "목살"));
        synonyms.put("삼겹살", List.of("고기", "돼지고기", "구이"));
        synonyms.put("파스타", List.of("양식", "스파게티", "이탈리안"));
        synonyms.put("초밥", List.of("일식", "스시", "회"));
        synonyms.put("짜장면", List.of("중식", "중화요리", "중국집"));
        synonyms.put("돈까스", List.of("돈가스", "일식"));
        synonyms.put("커피숍", List.of("카페", "커피"));
        synonyms.put("갬성", List.of("감성", "사진"));

        // Long particles must be checked before their suffixes.
        postpositions.addAll(List.of(
                "에서부터", "으로부터", "이랑", "에게", "에서", "으로", "로는", "에는", "까지", "부터",
                "랑", "하고", "께", "은", "는", "이", "가", "을", "를", "에", "와", "과", "도", "로"
        ));
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
