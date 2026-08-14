package com.example.backend.business.dto.request;

import com.example.backend.restaurant.domain.type.RestaurantStatus;
import jakarta.validation.constraints.NotNull;

public record BusinessRestaurantStatusUpdateRequest(
        @NotNull(message = "변경할 운영 상태는 필수입니다.")
        RestaurantStatus status
) {
}
