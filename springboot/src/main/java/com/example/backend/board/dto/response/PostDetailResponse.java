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
        List<MediaResponse> media,
        long viewCount,
        long commentCount,
        long likeCount,
        boolean likedByCurrentUser,
        boolean ownedByCurrentUser,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public PostDetailResponse {
        media = media == null ? List.of() : List.copyOf(media);
    }

    public record MediaResponse(
            Long postMediaId,
            String mediaType,
            String mediaUrl,
            int displayOrder,
            LocalDateTime createdAt,
            String processingStatus,
            int processingProgress,
            String processingMessage
    ) {
        public MediaResponse {
            processingStatus = processingStatus == null || processingStatus.isBlank()
                    ? "READY"
                    : processingStatus;
            processingProgress = Math.max(0, Math.min(100, processingProgress));
        }
    }
}
