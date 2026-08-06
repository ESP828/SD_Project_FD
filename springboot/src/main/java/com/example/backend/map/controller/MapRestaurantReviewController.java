package com.example.backend.map.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.review.dto.request.ReviewCreateRequest;
import com.example.backend.review.dto.response.ReviewResponse;
import com.example.backend.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공공데이터 출처 음식점에 로그인한 사용자가 리뷰를 남기는 API.
 * 조회는 {@code /api/public/map/restaurants/{id}/reviews}(인증 불필요)를 쓰고,
 * 작성은 로그인이 필요해 permitAll 대상인 /api/public/** 밖(/api/map/**)에 둔다.
 */
@RestController
@RequestMapping("/api/map/restaurants")
public class MapRestaurantReviewController {

    private final ReviewService reviewService;

    public MapRestaurantReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/{id}/reviews")
    public ApiResponse<ReviewResponse> createReview(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody ReviewCreateRequest request
    ) {
        return ApiResponse.success(
                "리뷰가 등록되었습니다.",
                reviewService.createReviewForPublicRestaurant(id, account.accountId(), request)
        );
    }
}
