package com.example.backend.admin.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AdminPresetRestaurantOrderRequest(
        @NotEmpty List<@Valid Item> restaurants
) {
    public record Item(
            @NotNull @Positive Long restaurantId,
            @PositiveOrZero Integer displayOrder
    ) {
    }
}
