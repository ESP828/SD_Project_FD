package com.example.backend.review.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.review.dto.request.ReviewUpdateRequest;
import com.example.backend.review.dto.response.ReviewResponse;
import com.example.backend.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class ReviewManagementController {

    private final ReviewService reviewService;

    public ReviewManagementController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PutMapping("/{reviewId}")
    public ApiResponse<ReviewResponse> updateReview(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody ReviewUpdateRequest request
    ) {
        return ApiResponse.success(
                "리뷰가 수정되었습니다.",
                reviewService.updateReview(reviewId, account.accountId(), request)
        );
    }

    @DeleteMapping("/{reviewId}")
    public ApiResponse<Void> deleteReview(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        reviewService.deleteReview(reviewId, account.accountId());
        return ApiResponse.success("리뷰가 삭제되었습니다.", null);
    }
}
