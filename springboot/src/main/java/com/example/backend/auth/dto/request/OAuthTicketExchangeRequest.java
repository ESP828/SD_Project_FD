package com.example.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OAuthTicketExchangeRequest(
        @NotBlank(message = "소셜 로그인 교환 코드가 필요합니다.")
        @Size(max = 200, message = "소셜 로그인 교환 코드가 너무 깁니다.")
        String ticket
) {
}
