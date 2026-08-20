package com.example.backend.review.dto.response;

public record ReviewMediaResponse(
        Long reviewMediaId,
        String mediaType,
        String url,
        String mimeType,
        String originalName,
        long fileSize,
        int displayOrder
) {
}
