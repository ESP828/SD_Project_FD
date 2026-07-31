package com.example.backend.restaurant.mapper;

import com.example.backend.restaurant.domain.entity.Restaurant;
import com.example.backend.restaurant.dto.response.RestaurantResponse;

public final class RestaurantMapper {

    private RestaurantMapper() {
    }

    public static RestaurantResponse toResponse(Restaurant restaurant) {

        return new RestaurantResponse(
                restaurant.getRestaurantId(),
                restaurant.getName(),
                restaurant.getCategory() != null
                        ? restaurant.getCategory().getName()
                        : null,
                restaurant.getAddress(),
                restaurant.getPhone(),
                restaurant.getOpeningHours(),
                restaurant.getStatus().name()
        );
    }

}
