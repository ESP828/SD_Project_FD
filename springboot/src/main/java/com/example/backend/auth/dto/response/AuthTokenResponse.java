package com.example.backend.auth.dto.response;

public record AuthTokenResponse(
        String token,
        String tokenType,
        long expiresIn
) {
    public static AuthTokenResponse bearer(String token, long expiresIn) {
        return new AuthTokenResponse(token, "Bearer", expiresIn);
    }
}
