package com.example.backend.restaurant.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.news.dto.response.RestaurantNewsResponse;
import com.example.backend.news.service.RestaurantNewsService;
import com.example.backend.restaurant.dto.response.MenuResponse;
import com.example.backend.restaurant.dto.response.RestaurantDetailResponse;
import com.example.backend.restaurant.service.RestaurantService;
import com.example.backend.review.dto.response.ReviewResponse;
import com.example.backend.review.service.ReviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 사업자가 우리 사이트에 직접 등록한 음식점의 상세·메뉴·리뷰·소식 조회.
 * 인증 없이도 조회 가능하지만, 로그인 상태면 찜 여부가 같이 내려간다.
 */
@RestController
@RequestMapping("/api/public/restaurants")
public class RestaurantDetailController {

    private final RestaurantService restaurantService;
    private final ReviewService reviewService;
    private final RestaurantNewsService restaurantNewsService;

    public RestaurantDetailController(
            RestaurantService restaurantService,
            ReviewService reviewService,
            RestaurantNewsService restaurantNewsService
    ) {
        this.restaurantService = restaurantService;
        this.reviewService = reviewService;
        this.restaurantNewsService = restaurantNewsService;
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
    public ApiResponse<List<MenuResponse>> getMenu(@PathVariable Long restaurantId) {
        return ApiResponse.success(restaurantService.getMenu(restaurantId));
    }

    @GetMapping("/{restaurantId}/reviews")
    public ApiResponse<List<ReviewResponse>> getReviews(@PathVariable Long restaurantId) {
        return ApiResponse.success(reviewService.getReviews(restaurantId));
    }

    @GetMapping("/{restaurantId}/news")
    public ApiResponse<List<RestaurantNewsResponse>> getNews(@PathVariable Long restaurantId) {
        return ApiResponse.success(restaurantNewsService.getNews(restaurantId));
    }
}
