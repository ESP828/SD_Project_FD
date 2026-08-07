package com.example.backend.review.controller;

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
 * 우리 사이트에 등록된 음식점에 로그인한 사용자가 리뷰를 남기는 API.
 * 계정당 음식점 하나에 리뷰 하나만 허용한다.
 */
@RestController
@RequestMapping("/api/restaurants")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/{restaurantId}/reviews")
    public ApiResponse<ReviewResponse> createReview(
            @PathVariable Long restaurantId,
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody ReviewCreateRequest request
    ) {
        return ApiResponse.success(
                "리뷰가 등록되었습니다.",
                reviewService.createReview(restaurantId, account.accountId(), request)
        );
    }
}
