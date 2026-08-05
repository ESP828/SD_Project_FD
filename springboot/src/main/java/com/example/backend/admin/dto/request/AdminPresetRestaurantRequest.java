package com.example.backend.admin.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

public record AdminPresetRestaurantRequest(
        @NotNull @Positive Long restaurantId,
        @PositiveOrZero Integer displayOrder,
        @Size(max = 255) String description
) {
}
