package com.example.backend.review.service;

import com.example.backend.review.dto.response.ReviewResponse;
import com.example.backend.review.repository.ReviewRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReviewService {

    private static final int MAX_RESULTS = 50;

    private final ReviewRepository reviewRepository;

    public ReviewService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviews(Long restaurantId) {
        return reviewRepository.findActiveByRestaurantId(restaurantId, PageRequest.of(0, MAX_RESULTS)).stream()
                .map(ReviewResponse::from)
                .toList();
    }
}
