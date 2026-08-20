package com.example.backend.search.dto.response;

/**
 * 통합 검색 결과 한 건. public_restaurant와 restaurant는 PK가 서로 겹치기 때문에
 * {@code sourceType}과 {@code id}를 함께 봐야 실제 음식점을 특정할 수 있다.
 */
public record RestaurantSearchItemResponse(
        String sourceType,
        Long id,
        String name,
        String categoryName,
        String roadAddress,
        String lotAddress,
        Double lat,
        Double lon
) {
}
