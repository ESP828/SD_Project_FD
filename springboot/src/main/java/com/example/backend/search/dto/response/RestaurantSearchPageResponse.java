package com.example.backend.search.dto.response;

import java.util.List;

/**
 * 통합 검색 목록 응답. 기존 {@code PublicRestaurantSearchResponse}와 필드 구성을 맞춰
 * 검색 화면의 페이징 처리를 그대로 재사용할 수 있게 한다.
 */
public record RestaurantSearchPageResponse(
        List<RestaurantSearchItemResponse> items,
        int page,
        int totalPages,
        long totalCount,
        boolean hasPrevPage,
        boolean hasNextPage
) {
    public RestaurantSearchPageResponse {
        items = List.copyOf(items);
    }
}
