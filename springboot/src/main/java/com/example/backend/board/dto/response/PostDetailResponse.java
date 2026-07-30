package com.example.backend.board.dto.response;

import com.example.backend.board.domain.type.BoardType;
import com.example.backend.board.domain.type.PostCategory;

import java.time.LocalDateTime;

public record PostDetailResponse(
        Long postId,
        String title,
        String content,
        Long authorAccountId,
        String authorLoginId,
        String authorNickname,
        String authorRole,
        BoardType boardType,
        PostCategory category,
        Long restaurantId,
        RestaurantSummaryResponse restaurant,
        long viewCount,
        long commentCount,
        long likeCount,
        boolean likedByCurrentUser,
        boolean ownedByCurrentUser,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
