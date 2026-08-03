package com.example.backend.news.repository;

import com.example.backend.news.domain.entity.NewsPost;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsPostRepository extends JpaRepository<NewsPost, Long> {
}
