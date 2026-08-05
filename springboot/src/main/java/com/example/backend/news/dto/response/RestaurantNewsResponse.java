package com.example.backend.news.dto.response;

import com.example.backend.news.domain.entity.RestaurantNews;

import java.time.LocalDateTime;

public record RestaurantNewsResponse(
        Long newsId,
        String title,
        String content,
        String imageUrl,
        LocalDateTime createdAt
) {
    public static RestaurantNewsResponse from(RestaurantNews news) {
        return new RestaurantNewsResponse(
                news.getNewsId(),
                news.getTitle(),
                news.getContent(),
                news.getImageUrl(),
                news.getCreatedAt()
        );
    }
}
