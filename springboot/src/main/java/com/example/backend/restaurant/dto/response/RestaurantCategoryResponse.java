package com.example.backend.restaurant.dto.response;

import com.example.backend.restaurant.domain.entity.RestaurantCategory;

public record RestaurantCategoryResponse(
        Integer categoryId,
        String categoryCode,
        String name,
        Integer parentId
) {
    public static RestaurantCategoryResponse from(RestaurantCategory category) {
        return new RestaurantCategoryResponse(
                category.getCategoryId(),
                category.getCategoryCode(),
                category.getName(),
                category.getParent() == null ? null : category.getParent().getCategoryId()
        );
    }
}
