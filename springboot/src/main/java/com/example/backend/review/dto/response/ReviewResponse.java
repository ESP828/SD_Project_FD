package com.example.backend.review.dto.response;

import com.example.backend.auth.domain.type.AccountStatus;
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
                displayNickname(review),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt()
        );
    }

    // 관리자가 계정을 삭제해도 실제로는 소프트 삭제(status=WITHDRAWN)라서 계정 행과 작성자
    // 연관관계는 그대로 남는다. 탈퇴한 회원의 실명이 계속 노출되지 않도록 여기서 가려준다.
    private static String displayNickname(Review review) {
        var account = review.getAccount();
        if (account == null || account.getStatus() == AccountStatus.WITHDRAWN) {
            return "탈퇴한 회원";
        }
        return account.getNickname();
    }
}
