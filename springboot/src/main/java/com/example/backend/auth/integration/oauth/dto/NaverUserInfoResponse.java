package com.example.backend.auth.integration.oauth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverUserInfoResponse(
        String resultcode,
        String message,
        Profile response
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(
            String id,
            String email,
            String nickname,
            String name
    ) {
    }
}
