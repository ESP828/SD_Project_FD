package com.example.backend.mypage.dto.response;

import java.time.LocalDateTime;

/**
 * 마이페이지 상세 탭에서 사용하는 읽기 전용 활동 항목.
 */
public final class MyPageActivityResponse {

    private MyPageActivityResponse() {
    }

    public record FavoriteItem(
            Long restaurantId,
            String restaurantName,
            String categoryName,
            String address,
            String description,
            LocalDateTime createdAt
    ) {
    }

    public record ReviewItem(
            Long reviewId,
            Long restaurantId,
            String restaurantName,
            int rating,
            String content,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record PostItem(
            Long postId,
            String boardType,
            String category,
            String title,
            long viewCount,
            long likeCount,
            long commentCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record CommentItem(
            Long commentId,
            Long postId,
            String postTitle,
            String content,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record NotificationItem(
            Long notificationId,
            String type,
            String content,
            String targetType,
            Long targetId,
            String targetUrl,
            boolean read,
            LocalDateTime createdAt
    ) {
    }
}
