package com.example.backend.admin.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdminAccountUpdateRequest(
        @NotBlank(message = "권한을 선택해 주세요.")
        String role,

        @NotBlank(message = "상태를 선택해 주세요.")
        String status
) {
}
