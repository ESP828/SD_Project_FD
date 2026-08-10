package com.example.backend.board.dto.response;

import java.util.List;

public record CommentPageResponse(
        List<CommentResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        long totalCommentCount
) {
    public CommentPageResponse {
        content = List.copyOf(content);
    }

    public CommentPageResponse(
            List<CommentResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last
    ) {
        this(
                content,
                page,
                size,
                totalElements,
                totalPages,
                first,
                last,
                totalElements
        );
    }
}
