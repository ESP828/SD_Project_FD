package com.example.backend.news.dto.response;

import java.time.LocalDateTime;

public class NewsPostResponse {
    private Long id;
    private String title;
    private String content;
    private LocalDateTime createdAt;

    public NewsPostResponse(Long id, String title, String content, LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
