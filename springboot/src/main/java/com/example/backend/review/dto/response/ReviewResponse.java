package com.example.backend.review.dto.response;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.domain.type.AccountStatus;
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
                displayNickname(review.getAccount()),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt(),
                viewerAccountId != null && viewerAccountId.equals(authorAccountId)
        );
    }

    // 관리자가 계정을 삭제해도 실제로는 소프트 삭제(status=WITHDRAWN)라서 계정 행과 작성자
    // 연관관계는 그대로 남는다. 탈퇴한 회원의 실명이 계속 노출되지 않도록 여기서 가려준다.
    private static String displayNickname(Account account) {
        if (account == null || account.getStatus() == AccountStatus.WITHDRAWN) {
            return "탈퇴한 회원";
        }
        return account.getNickname();
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
