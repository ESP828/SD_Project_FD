package com.example.backend.map.controller;

import com.example.backend.favorite.service.PublicRestaurantFavoriteService;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.map.dto.response.PublicRestaurantDetailResponse;
import com.example.backend.map.dto.response.PublicRestaurantMarkerResponse;
import com.example.backend.map.dto.response.PublicRestaurantSearchResponse;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import com.example.backend.restaurant.dto.response.MenuResponse;
import com.example.backend.restaurant.query.PublicRestaurantMenuQueryRepository;
import com.example.backend.restaurant.repository.PublicRestaurantRepository;
import com.example.backend.restaurant.repository.PublicRestaurantSpecifications;
import com.example.backend.review.dto.response.ReviewResponse;
import com.example.backend.review.service.ReviewService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 지도 화면과 검색 화면에 표시할 음식점 정보를 공공데이터 기반 자체 DB에서 조회한다(카카오 장소검색 미사용).
 */
@RestController
@RequestMapping("/api/public/map")
public class MapRestaurantController {

    private static final int MAX_RESULTS = 30;
    private static final int MAX_SEARCH_PAGE_SIZE = 50;

    private final PublicRestaurantRepository publicRestaurantRepository;
    private final ReviewService reviewService;
    private final PublicRestaurantFavoriteService favoriteService;
    private final PublicRestaurantMenuQueryRepository menuQueryRepository;

    public MapRestaurantController(
            PublicRestaurantRepository publicRestaurantRepository,
            ReviewService reviewService,
            PublicRestaurantFavoriteService favoriteService,
            PublicRestaurantMenuQueryRepository menuQueryRepository
    ) {
        this.publicRestaurantRepository = publicRestaurantRepository;
        this.reviewService = reviewService;
        this.favoriteService = favoriteService;
        this.menuQueryRepository = menuQueryRepository;
    }

    @GetMapping("/restaurants")
    public ApiResponse<List<PublicRestaurantMarkerResponse>> searchInBounds(
            @RequestParam BigDecimal swLat,
            @RequestParam BigDecimal swLng,
            @RequestParam BigDecimal neLat,
            @RequestParam BigDecimal neLng,
            @RequestParam(required = false) String keyword
    ) {
        List<PublicRestaurant> restaurants = StringUtils.hasText(keyword)
                ? publicRestaurantRepository.searchInBoundsByRelevance(
                        swLat, neLat, swLng, neLng, keyword.trim(), MAX_RESULTS
                )
                : publicRestaurantRepository.findByLatitudeBetweenAndLongitudeBetween(
                        swLat, neLat, swLng, neLng, PageRequest.of(0, MAX_RESULTS)
                );
        List<PublicRestaurantMarkerResponse> response = restaurants.stream()
                .map(PublicRestaurantMarkerResponse::from)
                .toList();
        return ApiResponse.success(response);
    }

    @GetMapping("/restaurants/{id}")
    public ApiResponse<PublicRestaurantDetailResponse> getDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        PublicRestaurant restaurant = publicRestaurantRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        long favoriteCount = favoriteService.count(id);
        boolean favoritedByMe = account != null && favoriteService.isFavorited(id, account.accountId());
        return ApiResponse.success(PublicRestaurantDetailResponse.from(restaurant, favoriteCount, favoritedByMe));
    }

    @GetMapping("/restaurants/{id}/reviews")
    public ApiResponse<List<ReviewResponse>> getReviews(@PathVariable Long id) {
        return ApiResponse.success(reviewService.getReviewsForPublicRestaurant(id));
    }

    @GetMapping("/restaurants/{id}/menu")
    public ApiResponse<List<MenuResponse>> getMenu(@PathVariable Long id) {
        return ApiResponse.success(menuQueryRepository.findVisibleByPublicRestaurantId(id));
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
                ? List.of(category)
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
