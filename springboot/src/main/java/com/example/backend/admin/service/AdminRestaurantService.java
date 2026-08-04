package com.example.backend.admin.service;

import com.example.backend.admin.dto.request.AdminRestaurantUpdateRequest;
import com.example.backend.admin.dto.response.AdminRestaurantResponse;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import com.example.backend.restaurant.domain.entity.Restaurant;
import com.example.backend.restaurant.domain.type.RestaurantStatus;
import com.example.backend.restaurant.repository.RestaurantRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminRestaurantService {

    private static final int MAX_RESULTS = 100;

    private final RestaurantRepository restaurantRepository;

    public AdminRestaurantService(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminRestaurantResponse> search(String keyword, String statusFilter) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        RestaurantStatus status = parseStatusOrNull(statusFilter);
        Pageable limit = PageRequest.of(0, MAX_RESULTS);
        return restaurantRepository.search(normalizedKeyword, status, limit).stream()
                .map(AdminRestaurantResponse::from)
                .toList();
    }

    @Transactional
    public void update(Long restaurantId, AdminRestaurantUpdateRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));

        restaurant.update(
                restaurant.getCategory(),
                request.name(),
                request.address(),
                request.addressDetail(),
                restaurant.getLatitude(),
                restaurant.getLongitude(),
                request.phone(),
                request.openingHours(),
                request.closedDays(),
                request.description()
        );

        applyStatus(restaurant, parseStatus(request.status()));
    }

    private RestaurantStatus parseStatus(String raw) {
        try {
            return RestaurantStatus.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private RestaurantStatus parseStatusOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return parseStatus(raw);
    }

    private void applyStatus(Restaurant restaurant, RestaurantStatus status) {
        switch (status) {
            case ACTIVE -> restaurant.activate();
            case INACTIVE -> restaurant.deactivate();
            case DELETED -> restaurant.delete();
        }
    }
}
