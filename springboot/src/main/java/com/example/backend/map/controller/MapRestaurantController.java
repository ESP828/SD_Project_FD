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
import com.example.backend.review.domain.entity.Review;
import com.example.backend.review.dto.response.ReviewResponse;
import com.example.backend.review.integration.sentiment.SentimentAnalysisClient;
import com.example.backend.review.integration.sentiment.dto.RestaurantSentimentSummaryRequest;
import com.example.backend.review.integration.sentiment.dto.RestaurantSentimentSummaryResponse;
import com.example.backend.review.service.ReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(MapRestaurantController.class);
    private static final int MAX_RESULTS = 30;
    private static final int MAX_SEARCH_PAGE_SIZE = 50;

    private final PublicRestaurantRepository publicRestaurantRepository;
    private final ReviewService reviewService;
    private final PublicRestaurantFavoriteService favoriteService;
    private final PublicRestaurantMenuQueryRepository menuQueryRepository;
    private final SentimentAnalysisClient sentimentAnalysisClient;

    public MapRestaurantController(
            PublicRestaurantRepository publicRestaurantRepository,
            ReviewService reviewService,
            PublicRestaurantFavoriteService favoriteService,
            PublicRestaurantMenuQueryRepository menuQueryRepository,
            SentimentAnalysisClient sentimentAnalysisClient
    ) {
        this.publicRestaurantRepository = publicRestaurantRepository;
        this.reviewService = reviewService;
        this.favoriteService = favoriteService;
        this.menuQueryRepository = menuQueryRepository;
        this.sentimentAnalysisClient = sentimentAnalysisClient;
    }

    @GetMapping("/restaurants")
    public ApiResponse<List<PublicRestaurantMarkerResponse>> searchInBounds(
            @RequestParam BigDecimal swLat,
            @RequestParam BigDecimal swLng,
            @RequestParam BigDecimal neLat,
            @RequestParam BigDecimal neLng,
            @RequestParam(required = false) String keyword
    ) {
        List<PublicRestaurant> restaurants;
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            // 상호명·카테고리·주소에 검색어가 그대로 포함된 매장을 우선 찾는다(정확한 매장명 검색 시
            // 그 매장만 나오도록). ngram 풀텍스트 검색은 부분 문자열이 겹치는 다른 매장까지 끌어오므로
            // 정확 매칭 결과가 없을 때만 폴백으로 사용한다.
            restaurants = publicRestaurantRepository.searchInBoundsByExactContains(
                    swLat, neLat, swLng, neLng, "%" + trimmed + "%", MAX_RESULTS
            );
            if (restaurants.isEmpty()) {
                restaurants = publicRestaurantRepository.searchInBoundsByRelevance(
                        swLat, neLat, swLng, neLng, trimmed, MAX_RESULTS
                );
            }
        } else {
            restaurants = publicRestaurantRepository.findByLatitudeBetweenAndLongitudeBetween(
                    swLat, neLat, swLng, neLng, PageRequest.of(0, MAX_RESULTS)
            );
        }
        List<PublicRestaurantMarkerResponse> response = restaurants.stream()
                .map(PublicRestaurantMarkerResponse::from)
                .toList();
        return ApiResponse.success(response);
    }

    @GetMapping("/restaurants/find-by-name")
    public ApiResponse<PublicRestaurantMarkerResponse> findByExactName(@RequestParam String name) {
        // 지도 반경 제한 없이 전체 DB에서 정확히 같은 이름의 매장을 찾는다. 이름이 겹치는 매장이
        // 둘 이상이면(같은 이름의 프랜차이즈 등) 어디로 이동해야 할지 알 수 없으므로 매칭시키지 않는다.
        List<PublicRestaurant> matches = publicRestaurantRepository.findByName(name.trim());
        PublicRestaurantMarkerResponse response = matches.size() == 1
                ? PublicRestaurantMarkerResponse.from(matches.get(0))
                : null;
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

    @GetMapping("/restaurants/{id}/reviews/page")
    public ApiResponse<ReviewResponse.PageResponse> getReviewPage(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedAccount account,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        Long viewerAccountId = account != null ? account.accountId() : null;
        return ApiResponse.success(reviewService.getReviewPageForPublicRestaurant(id, viewerAccountId, page, size));
    }

    @GetMapping("/restaurants/{id}/menu")
    public ApiResponse<List<MenuResponse>> getMenu(@PathVariable Long id) {
        return ApiResponse.success(menuQueryRepository.findVisibleByPublicRestaurantId(id));
    }

    /**
     * 이 매장의 리뷰들을 FastAPI 감성분석(Naive Bayes) 서비스로 보내 긍정/부정 비율을 집계한다.
     * 감성분석 서비스가 설정되어 있지 않거나(로컬에서 FastAPI 미기동 등) 호출에 실패하면
     * 화면이 깨지지 않도록 null을 반환한다 - 프론트에서는 "AI 리뷰 분석 준비 중" 등으로 처리한다.
     */
    @GetMapping("/restaurants/{id}/sentiment-summary")
    public ApiResponse<RestaurantSentimentSummaryResponse> getSentimentSummary(@PathVariable Long id) {
        if (!sentimentAnalysisClient.isConfigured()) {
            return ApiResponse.success(null);
        }
        PublicRestaurant restaurant = publicRestaurantRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        List<Review> reviews = reviewService.getAllReviewsForSentimentForPublicRestaurant(id);
        if (reviews.isEmpty()) {
            return ApiResponse.success(null);
        }
        List<RestaurantSentimentSummaryRequest.ReviewItem> reviewItems = reviews.stream()
                .map(r -> new RestaurantSentimentSummaryRequest.ReviewItem(r.getContent(), r.getRating()))
                .toList();
        try {
            RestaurantSentimentSummaryResponse summary = sentimentAnalysisClient.summarizeRestaurant(
                    id, restaurant.getName(), reviewItems
            );
            return ApiResponse.success(summary);
        } catch (RuntimeException e) {
            log.warn("감성분석 서비스 호출 실패 (restaurantId={}): {}", id, e.getMessage());
            return ApiResponse.success(null);
        }
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
