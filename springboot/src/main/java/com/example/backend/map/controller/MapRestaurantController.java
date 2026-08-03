package com.example.backend.map.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.map.dto.response.PublicRestaurantMarkerResponse;
import com.example.backend.map.dto.response.PublicRestaurantSearchResponse;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import com.example.backend.restaurant.repository.PublicRestaurantRepository;
import com.example.backend.restaurant.repository.PublicRestaurantSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 지도 화면과 검색 화면에 표시할 음식점 정보를 공공데이터 기반 자체 DB에서 조회한다(카카오 장소검색 미사용).
 */
@RestController
@RequestMapping("/api/public/map")
public class MapRestaurantController {

    private static final int MAX_RESULTS = 300;
    private static final int MAX_SEARCH_PAGE_SIZE = 50;

    /**
     * 검색 화면의 음식 종류 필터(대분류)를 공공데이터 상권업종 중분류/소분류 명칭으로 매핑한다.
     * 예) 검색 화면의 "카페"는 공공데이터의 중분류 "비알코올" 아래 소분류 "카페"로 적재되어 있다.
     */
    private static final Map<String, List<String>> CATEGORY_ALIASES = Map.of(
            "한식", List.of("한식"),
            "중식", List.of("중식"),
            "일식", List.of("일식"),
            "양식", List.of("서양식"),
            "카페", List.of("카페"),
            "디저트", List.of("빵/도넛", "떡/한과", "아이스크림/빙수")
    );

    private final PublicRestaurantRepository publicRestaurantRepository;

    public MapRestaurantController(PublicRestaurantRepository publicRestaurantRepository) {
        this.publicRestaurantRepository = publicRestaurantRepository;
    }

    @GetMapping("/restaurants")
    public ApiResponse<List<PublicRestaurantMarkerResponse>> searchInBounds(
            @RequestParam BigDecimal swLat,
            @RequestParam BigDecimal swLng,
            @RequestParam BigDecimal neLat,
            @RequestParam BigDecimal neLng,
            @RequestParam(required = false) String keyword
    ) {
        Pageable limit = PageRequest.of(0, MAX_RESULTS);
        List<PublicRestaurant> restaurants = StringUtils.hasText(keyword)
                ? publicRestaurantRepository.findByLatitudeBetweenAndLongitudeBetweenAndNameContaining(
                        swLat, neLat, swLng, neLng, keyword.trim(), limit
                )
                : publicRestaurantRepository.findByLatitudeBetweenAndLongitudeBetween(
                        swLat, neLat, swLng, neLng, limit
                );
        List<PublicRestaurantMarkerResponse> response = restaurants.stream()
                .map(PublicRestaurantMarkerResponse::from)
                .toList();
        return ApiResponse.success(response);
    }

    @GetMapping("/restaurants/search")
    public ApiResponse<PublicRestaurantSearchResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<String> categoryNames = StringUtils.hasText(category)
                ? CATEGORY_ALIASES.get(category)
                : null;
        Specification<PublicRestaurant> spec = Specification
                .where(PublicRestaurantSpecifications.nameContains(keyword))
                .and(PublicRestaurantSpecifications.regionContains(region))
                .and(PublicRestaurantSpecifications.categoryIn(categoryNames));

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_SEARCH_PAGE_SIZE)
        );
        Page<PublicRestaurantMarkerResponse> result = publicRestaurantRepository
                .findAll(spec, pageable)
                .map(PublicRestaurantMarkerResponse::from);

        return ApiResponse.success(PublicRestaurantSearchResponse.from(result));
    }
}
