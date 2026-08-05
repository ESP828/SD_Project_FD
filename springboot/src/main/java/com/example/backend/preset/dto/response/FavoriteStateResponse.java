package com.example.backend.preset.dto.response;

public record FavoriteStateResponse(
        long favoriteCount,
        boolean favoriteByCurrentUser
) {
}
