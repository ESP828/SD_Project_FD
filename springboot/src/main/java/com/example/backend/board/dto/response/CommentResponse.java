package com.example.backend.board.dto.response;

import java.time.LocalDateTime;

public record CommentResponse(
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
}
