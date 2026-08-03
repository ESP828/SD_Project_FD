package com.example.backend.news.service;

import com.example.backend.news.domain.entity.NewsPost;
import com.example.backend.news.dto.request.NewsPostCreateRequest;
import com.example.backend.news.dto.response.NewsPostResponse;
import com.example.backend.news.repository.NewsPostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class NewsPostService {

    private final NewsPostRepository newsPostRepository;

    public NewsPostService(NewsPostRepository newsPostRepository) {
        this.newsPostRepository = newsPostRepository;
    }

    public NewsPostResponse create(NewsPostCreateRequest request) {
        NewsPost post = new NewsPost(request.getTitle(), request.getContent());
        NewsPost saved = newsPostRepository.save(post);
        return new NewsPostResponse(saved.getId(), saved.getTitle(), saved.getContent(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<NewsPostResponse> list() {
        return newsPostRepository.findAll().stream()
                .map(post -> new NewsPostResponse(post.getId(), post.getTitle(), post.getContent(), post.getCreatedAt()))
                .toList();
    }
}
