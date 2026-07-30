package com.example.backend.board.dto.response;

import java.util.List;

public record PostPageResponse(
        List<PostListItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public PostPageResponse {
        content = List.copyOf(content);
    }
}
