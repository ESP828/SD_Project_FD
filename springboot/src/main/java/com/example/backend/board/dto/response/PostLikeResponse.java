package com.example.backend.board.dto.response;

public record PostLikeResponse(
        Long postId,
        long likeCount,
        boolean liked
) {
}
