package com.example.backend.news.controller;

import com.example.backend.news.dto.request.NewsPostCreateRequest;
import com.example.backend.news.dto.response.NewsPostResponse;
import com.example.backend.news.service.NewsPostService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/news")
public class NewsPostController {

    private final NewsPostService newsPostService;

    public NewsPostController(NewsPostService newsPostService) {
        this.newsPostService = newsPostService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NewsPostResponse create(@RequestBody NewsPostCreateRequest request) {
        return newsPostService.create(request);
    }

    @GetMapping
    public List<NewsPostResponse> list() {
        return newsPostService.list();
    }
}
