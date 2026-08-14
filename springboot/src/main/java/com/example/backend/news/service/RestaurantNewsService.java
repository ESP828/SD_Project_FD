package com.example.backend.news.service;

import com.example.backend.news.domain.entity.RestaurantNews;
import com.example.backend.news.dto.request.RestaurantNewsCreateRequest;
import com.example.backend.news.dto.response.RestaurantNewsResponse;
import com.example.backend.news.exception.RestaurantNewsForbiddenException;
import com.example.backend.news.repository.RestaurantNewsRepository;
import com.example.backend.restaurant.domain.entity.Restaurant;
import com.example.backend.restaurant.exception.RestaurantNotFoundException;
import com.example.backend.restaurant.repository.RestaurantRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RestaurantNewsService {

    private static final int MAX_RESULTS = 50;

    private final RestaurantNewsRepository restaurantNewsRepository;
    private final RestaurantRepository restaurantRepository;

    public RestaurantNewsService(
            RestaurantNewsRepository restaurantNewsRepository,
            RestaurantRepository restaurantRepository
    ) {
        this.restaurantNewsRepository = restaurantNewsRepository;
        this.restaurantRepository = restaurantRepository;
    }

    @Transactional(readOnly = true)
    public List<RestaurantNewsResponse> getNews(Long restaurantId, Long viewerAccountId) {
        requireReadableRestaurant(restaurantId, viewerAccountId);
        return restaurantNewsRepository.findActiveByRestaurantId(restaurantId, PageRequest.of(0, MAX_RESULTS)).stream()
                .map(RestaurantNewsResponse::from)
                .toList();
    }

    @Transactional
    public RestaurantNewsResponse createNews(Long restaurantId, Long accountId, RestaurantNewsCreateRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(RestaurantNotFoundException::new);

        if (!restaurant.getOwner().getAccountId().equals(accountId)) {
            throw new RestaurantNewsForbiddenException();
        }

        RestaurantNews news = RestaurantNews.create(restaurant, request.title(), request.content(), request.imageUrl());
        restaurantNewsRepository.save(news);

        return RestaurantNewsResponse.from(news);
    }

    private Restaurant requireReadableRestaurant(Long restaurantId, Long viewerAccountId) {
        return restaurantRepository.findById(restaurantId)
                .filter(restaurant -> restaurant.isReadableBy(viewerAccountId))
                .orElseThrow(RestaurantNotFoundException::new);
    }
}
