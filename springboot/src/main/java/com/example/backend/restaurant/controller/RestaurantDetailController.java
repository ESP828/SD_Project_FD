package com.example.backend.restaurant.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.news.dto.response.RestaurantNewsResponse;
import com.example.backend.news.service.RestaurantNewsService;
import com.example.backend.restaurant.domain.entity.Restaurant;
import com.example.backend.restaurant.dto.response.MenuResponse;
import com.example.backend.restaurant.dto.response.RestaurantDetailResponse;
import com.example.backend.restaurant.exception.RestaurantNotFoundException;
import com.example.backend.restaurant.repository.RestaurantRepository;
import com.example.backend.restaurant.service.RestaurantService;
import com.example.backend.review.domain.entity.Review;
import com.example.backend.review.dto.response.ReviewResponse;
import com.example.backend.review.integration.sentiment.SentimentAnalysisClient;
import com.example.backend.review.integration.sentiment.dto.RestaurantSentimentSummaryRequest;
import com.example.backend.review.integration.sentiment.dto.RestaurantSentimentSummaryResponse;
import com.example.backend.review.service.ReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 사업자가 우리 사이트에 직접 등록한 음식점의 상세·메뉴·리뷰·소식 조회.
 * 인증 없이도 조회 가능하지만, 로그인 상태면 찜 여부가 같이 내려간다.
 */
@RestController
@RequestMapping("/api/public/restaurants")
public class RestaurantDetailController {

    private static final Logger log = LoggerFactory.getLogger(RestaurantDetailController.class);

    private final RestaurantService restaurantService;
    private final ReviewService reviewService;
    private final RestaurantNewsService restaurantNewsService;
    private final RestaurantRepository restaurantRepository;
    private final SentimentAnalysisClient sentimentAnalysisClient;

    public RestaurantDetailController(
            RestaurantService restaurantService,
            ReviewService reviewService,
            RestaurantNewsService restaurantNewsService,
            RestaurantRepository restaurantRepository,
            SentimentAnalysisClient sentimentAnalysisClient
    ) {
        this.restaurantService = restaurantService;
        this.reviewService = reviewService;
        this.restaurantNewsService = restaurantNewsService;
        this.restaurantRepository = restaurantRepository;
        this.sentimentAnalysisClient = sentimentAnalysisClient;
    }

    @GetMapping("/{restaurantId}")
    public ApiResponse<RestaurantDetailResponse> getDetail(
            @PathVariable Long restaurantId,
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        Long viewerAccountId = account != null ? account.accountId() : null;
        return ApiResponse.success(restaurantService.getDetail(restaurantId, viewerAccountId));
    }

    @GetMapping("/{restaurantId}/menu")
    public ApiResponse<List<MenuResponse>> getMenu(
            @PathVariable Long restaurantId,
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        Long viewerAccountId = account != null ? account.accountId() : null;
        return ApiResponse.success(restaurantService.getMenu(restaurantId, viewerAccountId));
    }

    @GetMapping("/{restaurantId}/reviews/page")
    public ApiResponse<ReviewResponse.PageResponse> getReviewPage(
            @PathVariable Long restaurantId,
            @AuthenticationPrincipal AuthenticatedAccount account,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        Long viewerAccountId = account != null ? account.accountId() : null;
        return ApiResponse.success(reviewService.getReviewPage(restaurantId, viewerAccountId, page, size));
    }

    /**
     * 사업자가 직접 등록한 매장의 리뷰들을 FastAPI 감성분석(Naive Bayes) 서비스로 보내
     * 긍정/부정 비율을 집계한다. MapRestaurantController의 공공데이터 매장용 로직과 동일하며,
     * 감성분석 서비스가 꺼져 있거나 호출에 실패해도 화면이 깨지지 않도록 null을 반환한다.
     */
    @GetMapping("/{restaurantId}/sentiment-summary")
    public ApiResponse<RestaurantSentimentSummaryResponse> getSentimentSummary(@PathVariable Long restaurantId) {
        if (!sentimentAnalysisClient.isConfigured()) {
            return ApiResponse.success(null);
        }
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .filter(Restaurant::isActive)
                .orElseThrow(RestaurantNotFoundException::new);
        List<Review> reviews = reviewService.getAllReviewsForSentimentForRestaurant(restaurantId);
        if (reviews.isEmpty()) {
            return ApiResponse.success(null);
        }
        List<RestaurantSentimentSummaryRequest.ReviewItem> reviewItems = reviews.stream()
                .map(r -> new RestaurantSentimentSummaryRequest.ReviewItem(r.getContent(), r.getRating()))
                .toList();
        try {
            RestaurantSentimentSummaryResponse summary = sentimentAnalysisClient.summarizeRestaurant(
                    restaurantId, restaurant.getName(), reviewItems
            );
            return ApiResponse.success(summary);
        } catch (RuntimeException e) {
            log.warn("감성분석 서비스 호출 실패 (restaurantId={}): {}", restaurantId, e.getMessage());
            return ApiResponse.success(null);
        }
    }

    @GetMapping("/{restaurantId}/news")
    public ApiResponse<List<RestaurantNewsResponse>> getNews(
            @PathVariable Long restaurantId,
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        Long viewerAccountId = account != null ? account.accountId() : null;
        return ApiResponse.success(restaurantNewsService.getNews(restaurantId, viewerAccountId));
    }
}
