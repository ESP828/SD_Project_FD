package com.example.backend.news.service;

import com.example.backend.news.dto.response.RestaurantNewsResponse;
import com.example.backend.news.repository.RestaurantNewsRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RestaurantNewsService {

    private static final int MAX_RESULTS = 50;

    private final RestaurantNewsRepository restaurantNewsRepository;

    public RestaurantNewsService(RestaurantNewsRepository restaurantNewsRepository) {
        this.restaurantNewsRepository = restaurantNewsRepository;
    }

    @Transactional(readOnly = true)
    public List<RestaurantNewsResponse> getNews(Long restaurantId) {
        return restaurantNewsRepository.findActiveByRestaurantId(restaurantId, PageRequest.of(0, MAX_RESULTS)).stream()
                .map(RestaurantNewsResponse::from)
                .toList();
    }
}
