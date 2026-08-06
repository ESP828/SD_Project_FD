package com.example.backend.board.dto.response;

import com.example.backend.board.domain.type.BoardType;
import com.example.backend.board.domain.type.PostCategory;

import java.time.LocalDateTime;
import java.util.List;

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
        LocalDateTime updatedAt,
        List<MediaResponse> media
) {
    public PostDetailResponse {
        media = media == null ? List.of() : List.copyOf(media);
    }

    public record MediaResponse(
            Long postMediaId,
            String mediaType,
            String mediaUrl,
            String mimeType,
            String originalName,
            long fileSize,
            int displayOrder
    ) {
    }
}
