package com.example.backend.review.dto.response;

import com.example.backend.review.domain.entity.Review;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long reviewId,
        String authorNickname,
        byte rating,
        String content,
        LocalDateTime createdAt
) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getReviewId(),
                review.getAccount() != null ? review.getAccount().getNickname() : "탈퇴한 회원",
                review.getRating(),
                review.getContent(),
                review.getCreatedAt()
        );
    }
}
