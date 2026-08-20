package com.example.backend.recommendation.dto.request;

/**
 * 자연어 맛집 추천 요청.
 * gender/ageGroup은 검색 화면의 상세 조건에서 이번 검색에만 적용하는 값으로, 회원 정보를 바꾸지 않는다.
 */
public record NaturalLanguageRecommendationRequest(
        String query,
        Double latitude,
        Double longitude,
        Integer radiusMeters,
        Integer limit,
        String gender,
        Integer ageGroup
) {
    public Integer radiusMeters() {
        return radiusMeters != null ? radiusMeters : 1500; // 기본 반경 1.5km
    }

    public Integer limit() {
        return limit != null ? limit : 5; // 기본 5개 추천
    }
}
