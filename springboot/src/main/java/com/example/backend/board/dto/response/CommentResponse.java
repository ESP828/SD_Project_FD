package com.example.backend.board.dto.response;

import java.time.LocalDateTime;

public record CommentResponse(
        Long commentId,
        Long postId,
        Long parentCommentId,
        Long authorAccountId,
        String authorLoginId,
        String authorNickname,
        String authorRole,
        String content,
        boolean hasImage,
        String imageUrl,
        String imageOriginalName,
        Long imageFileSize,
        boolean ownedByCurrentUser,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public CommentResponse(
            Long commentId,
            Long postId,
            Long authorAccountId,
            String authorLoginId,
            String authorNickname,
            String authorRole,
            String content,
            boolean ownedByCurrentUser,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(
                commentId,
                postId,
                null,
                authorAccountId,
                authorLoginId,
                authorNickname,
                authorRole,
                content,
                false,
                null,
                null,
                null,
                ownedByCurrentUser,
                createdAt,
                updatedAt
        );
    }
}
