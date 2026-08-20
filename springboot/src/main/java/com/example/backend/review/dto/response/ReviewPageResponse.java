package com.example.backend.review.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record ReviewPageResponse(
        List<ReviewResponse> items,
        int page,
        int totalPages,
        long totalCount,
        boolean hasPrevPage,
        boolean hasNextPage
) {
    public static ReviewPageResponse from(Page<ReviewResponse> page) {
        return new ReviewPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.hasPrevious(),
                page.hasNext()
        );
    }
}
