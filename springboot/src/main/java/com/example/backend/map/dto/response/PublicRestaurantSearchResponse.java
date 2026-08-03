package com.example.backend.map.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record PublicRestaurantSearchResponse(
        List<PublicRestaurantMarkerResponse> items,
        int page,
        int totalPages,
        long totalCount,
        boolean hasPrevPage,
        boolean hasNextPage
) {
    public static PublicRestaurantSearchResponse from(Page<PublicRestaurantMarkerResponse> page) {
        return new PublicRestaurantSearchResponse(
                page.getContent(),
                page.getNumber(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.hasPrevious(),
                page.hasNext()
        );
    }
}
