package com.example.backend.review.dto.response;

import com.example.backend.review.domain.entity.Review;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewResponse(
        Long reviewId,
        String authorNickname,
        byte rating,
        String content,
        LocalDateTime createdAt,
        boolean ownedByCurrentUser
) {
    public static ReviewResponse from(Review review) {
        return from(review, null);
    }

    public static ReviewResponse from(Review review, Long viewerAccountId) {
        Long authorAccountId = review.getAccount() != null ? review.getAccount().getAccountId() : null;
        return new ReviewResponse(
                review.getReviewId(),
                review.getAccount() != null ? review.getAccount().getNickname() : "탈퇴한 회원",
                review.getRating(),
                review.getContent(),
                review.getCreatedAt(),
                viewerAccountId != null && viewerAccountId.equals(authorAccountId)
        );
    }

    public record PageResponse(
            List<ReviewResponse> items,
            long totalElements,
            int totalPages,
            int page,
            int size,
            boolean first,
            boolean last,
            ReviewResponse myReview
    ) {
    }
}
