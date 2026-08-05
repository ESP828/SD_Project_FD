package com.example.backend.favorite.dto.response;

public record RestaurantFavoriteStateResponse(
        long favoriteCount,
        boolean favoriteByCurrentUser
) {
}
