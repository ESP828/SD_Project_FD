package com.example.backend.admin.service;

import com.example.backend.admin.dto.request.AdminRestaurantUpdateRequest;
import com.example.backend.admin.dto.response.AdminRestaurantResponse;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import com.example.backend.restaurant.domain.entity.Restaurant;
import com.example.backend.restaurant.domain.type.RestaurantStatus;
import com.example.backend.restaurant.repository.RestaurantRepository;
import com.example.backend.global.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminRestaurantService {

    private static final int MAX_PAGE_SIZE = 100;

    private final RestaurantRepository restaurantRepository;

    public AdminRestaurantService(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminRestaurantResponse> search(String keyword, String statusFilter, int page, int size) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        RestaurantStatus status = parseStatusOrNull(statusFilter);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(0, page), safeSize);
        Page<Restaurant> result = restaurantRepository.search(normalizedKeyword, status, pageable);
        List<AdminRestaurantResponse> content = result.getContent().stream()
                .map(AdminRestaurantResponse::from)
                .toList();
        return PageResponse.of(content, result.getNumber(), safeSize, result.getTotalElements());
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
