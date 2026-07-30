package com.example.backend.board.dto.response;

public record RestaurantSummaryResponse(
        Long restaurantId,
        String name,
        String address,
        String status
) {
}
