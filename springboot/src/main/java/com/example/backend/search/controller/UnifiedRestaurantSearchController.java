package com.example.backend.search.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.search.dto.response.RestaurantSearchItemResponse;
import com.example.backend.search.dto.response.RestaurantSearchPageResponse;
import com.example.backend.search.service.UnifiedRestaurantSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 공공데이터 음식점과 사업자 등록 음식점을 함께 조회하는 통합 검색 API.
 * 기존 {@code /api/public/map/**}(공공데이터 전용)는 그대로 두고 별도 경로로 제공한다.
 */
@RestController
@RequestMapping("/api/public/search")
public class UnifiedRestaurantSearchController {

    private final UnifiedRestaurantSearchService searchService;

    public UnifiedRestaurantSearchController(UnifiedRestaurantSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/restaurants")
    public ApiResponse<RestaurantSearchPageResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(searchService.search(keyword, region, category, page, size));
    }

    @GetMapping("/restaurants/bounds")
    public ApiResponse<List<RestaurantSearchItemResponse>> searchInBounds(
            @RequestParam BigDecimal swLat,
            @RequestParam BigDecimal swLng,
            @RequestParam BigDecimal neLat,
            @RequestParam BigDecimal neLng,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(searchService.searchInBounds(swLat, swLng, neLat, neLng, keyword));
    }

    @GetMapping("/restaurants/find-by-name")
    public ApiResponse<RestaurantSearchItemResponse> findByExactName(@RequestParam String name) {
        return ApiResponse.success(searchService.findByExactName(name));
    }
}
