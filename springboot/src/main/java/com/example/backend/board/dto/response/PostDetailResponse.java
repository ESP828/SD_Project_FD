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
        boolean pinned,
        Long restaurantId,
        Long publicRestaurantId,
        RestaurantSummaryResponse restaurant,
        long viewCount,
        long commentCount,
        long likeCount,
        boolean likedByCurrentUser,
        boolean ownedByCurrentUser,
        boolean newsManageableByCurrentUser,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean edited,
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
            int displayOrder,
            String processingStatus,
            int processingProgress,
            String processingMessage
    ) {
        public MediaResponse {
            processingStatus = processingStatus == null
                    ? "READY"
                    : processingStatus;
            processingProgress = Math.max(
                    0,
                    Math.min(100, processingProgress)
            );
        }

        public MediaResponse(
                Long postMediaId,
                String mediaType,
                String mediaUrl,
                String mimeType,
                String originalName,
                long fileSize,
                int displayOrder
        ) {
            this(
                    postMediaId,
                    mediaType,
                    mediaUrl,
                    mimeType,
                    originalName,
                    fileSize,
                    displayOrder,
                    "READY",
                    100,
                    null
            );
        }
    }
}
