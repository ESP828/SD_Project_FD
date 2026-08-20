package com.example.backend.review.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.review.dto.request.ReviewUpdateRequest;
import com.example.backend.review.dto.response.ReviewMediaResponse;
import com.example.backend.review.dto.response.ReviewResponse;
import com.example.backend.review.service.ReviewMediaService;
import com.example.backend.review.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class ReviewManagementController {

    private final ReviewService reviewService;
    private final ReviewMediaService reviewMediaService;

    public ReviewManagementController(
            ReviewService reviewService,
            ReviewMediaService reviewMediaService
    ) {
        this.reviewService = reviewService;
        this.reviewMediaService = reviewMediaService;
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

    @PostMapping(value = "/{reviewId}/media", consumes = MediaType.ALL_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewMediaResponse> uploadMedia(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal AuthenticatedAccount account,
            @RequestHeader("X-File-Name") String encodedFileName,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            HttpServletRequest request
    ) {
        try {
            return ApiResponse.success(
                    "리뷰 첨부파일이 등록되었습니다.",
                    reviewMediaService.upload(
                            reviewId,
                            account.accountId(),
                            encodedFileName,
                            contentType,
                            request.getInputStream(),
                            request.getContentLengthLong()
                    )
            );
        } catch (java.io.IOException exception) {
            throw new com.example.backend.global.exception.BusinessException(
                    com.example.backend.global.exception.ErrorCode.INVALID_INPUT,
                    "첨부파일 전송 데이터를 읽지 못했습니다."
            );
        }
    }

    @DeleteMapping("/{reviewId}/media/{reviewMediaId}")
    public ApiResponse<Void> deleteMedia(
            @PathVariable Long reviewId,
            @PathVariable Long reviewMediaId,
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        reviewMediaService.delete(reviewId, reviewMediaId, account.accountId());
        return ApiResponse.success("리뷰 첨부파일이 삭제되었습니다.", null);
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
