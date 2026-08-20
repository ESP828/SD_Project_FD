package com.example.backend.search.service;

import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import com.example.backend.restaurant.repository.PublicRestaurantRepository;
import com.example.backend.search.dto.response.RestaurantSearchItemResponse;
import com.example.backend.search.dto.response.RestaurantSearchPageResponse;
import com.example.backend.search.query.UnifiedRestaurantQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 검색 화면과 지도 화면이 공공데이터 음식점과 사업자 등록 음식점을 함께 볼 수 있게 해주는 통합 검색.
 */
@Service
@Transactional(readOnly = true)
public class UnifiedRestaurantSearchService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_BOUNDS_RESULTS = 30;

    private final UnifiedRestaurantQueryRepository queryRepository;
    private final PublicRestaurantRepository publicRestaurantRepository;

    public UnifiedRestaurantSearchService(
            UnifiedRestaurantQueryRepository queryRepository,
            PublicRestaurantRepository publicRestaurantRepository
    ) {
        this.queryRepository = queryRepository;
        this.publicRestaurantRepository = publicRestaurantRepository;
    }

    public RestaurantSearchPageResponse search(
            String keyword,
            String region,
            String category,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long totalCount = queryRepository.countSearch(keyword, region, category);
        int totalPages = (int) Math.ceil((double) totalCount / safeSize);
        List<RestaurantSearchItemResponse> items = totalCount == 0
                ? List.of()
                : queryRepository.search(keyword, region, category, safePage * safeSize, safeSize);
        return new RestaurantSearchPageResponse(
                items,
                safePage,
                totalPages,
                totalCount,
                safePage > 0,
                safePage + 1 < totalPages
        );
    }

    /**
     * 지도 영역 안의 음식점을 조회한다. 검색어가 있는데 정확 매칭 결과가 없으면,
     * 기존 지도 검색과 동일하게 공공데이터 풀텍스트 관련성 검색으로 폴백한다.
     */
    public List<RestaurantSearchItemResponse> searchInBounds(
            BigDecimal swLat,
            BigDecimal swLng,
            BigDecimal neLat,
            BigDecimal neLng,
            String keyword
    ) {
        List<RestaurantSearchItemResponse> items = queryRepository.searchInBounds(
                swLat, swLng, neLat, neLng, keyword, MAX_BOUNDS_RESULTS
        );
        if (!items.isEmpty() || keyword == null || keyword.isBlank()) {
            return items;
        }
        return publicRestaurantRepository
                .searchInBoundsByRelevance(swLat, neLat, swLng, neLng, keyword.trim(), MAX_BOUNDS_RESULTS)
                .stream()
                .map(UnifiedRestaurantSearchService::toPublicItem)
                .toList();
    }

    /**
     * 상호명이 정확히 일치하는 음식점이 딱 하나일 때만 반환한다(동명 매장이면 어디로 이동할지 알 수 없다).
     */
    public RestaurantSearchItemResponse findByExactName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        List<RestaurantSearchItemResponse> matches = queryRepository.findByExactName(name.trim());
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static RestaurantSearchItemResponse toPublicItem(PublicRestaurant restaurant) {
        String categoryName = restaurant.getCategorySmallName() != null
                ? restaurant.getCategorySmallName()
                : restaurant.getCategoryLargeName();
        return new RestaurantSearchItemResponse(
                "PUBLIC",
                restaurant.getPublicRestaurantId(),
                restaurant.getName(),
                categoryName,
                restaurant.getRoadAddress(),
                restaurant.getLotAddress(),
                restaurant.getLatitude() == null ? null : restaurant.getLatitude().doubleValue(),
                restaurant.getLongitude() == null ? null : restaurant.getLongitude().doubleValue()
        );
    }
}
