package com.example.backend.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminRestaurantUpdateRequest(
        @NotBlank(message = "상호명을 입력해 주세요.")
        @Size(max = 100)
        String name,

        @NotBlank(message = "주소를 입력해 주세요.")
        @Size(max = 255)
        String address,

        @Size(max = 255)
        String addressDetail,

        @Size(max = 30)
        String phone,

        @Size(max = 500)
        String openingHours,

        @Size(max = 255)
        String closedDays,

        String description,

        @NotBlank(message = "상태를 선택해 주세요.")
        String status
) {
}
