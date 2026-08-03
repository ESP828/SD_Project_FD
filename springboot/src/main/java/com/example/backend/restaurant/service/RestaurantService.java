package com.example.backend.restaurant.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.restaurant.domain.entity.*;
import com.example.backend.restaurant.dto.request.RestaurantCreateRequest;
import com.example.backend.restaurant.dto.response.RestaurantResponse;
import com.example.backend.restaurant.exception.*;
import com.example.backend.restaurant.mapper.RestaurantMapper;
import com.example.backend.restaurant.repository.RestaurantCategoryRepository;
import com.example.backend.restaurant.repository.RestaurantRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Transactional
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantCategoryRepository categoryRepository;
    private final AccountRepository accountRepository;

    public RestaurantService(
            RestaurantRepository restaurantRepository,
            RestaurantCategoryRepository categoryRepository,
            AccountRepository accountRepository
    ) {
        this.restaurantRepository = restaurantRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
    }

    public RestaurantResponse createRestaurant(
            Long accountId,
            RestaurantCreateRequest request
    ) {

        Account owner = accountRepository.findById(accountId)
                .orElseThrow(OwnerNotFoundException::new);

        RestaurantCategory category = null;

        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(CategoryNotFoundException::new);
        }
        if (restaurantRepository.existsByName(request.getName())) {
    throw new DuplicateRestaurantException();
}
        Restaurant restaurant = Restaurant.create(
                owner,
                category,
                request.getName(),
                request.getAddress(),
                request.getAddressDetail(),
                request.getLatitude() != null
                        ? BigDecimal.valueOf(request.getLatitude())
                        : null,
                request.getLongitude() != null
                        ? BigDecimal.valueOf(request.getLongitude())
                        : null,
                request.getPhone(),
                request.getOpeningHours(),
                request.getClosedDays(),
                request.getDescription()
        );

        restaurantRepository.save(restaurant);

        return RestaurantMapper.toResponse(restaurant);
    }

}
