package com.example.backend.board.dto.response;

public record RestaurantSummaryResponse(
        Long restaurantId,
        Long publicRestaurantId,
        String sourceType,
        String name,
        String address,
        String status
) {
    /** 기존 자체 등록 음식점 응답 생성 코드 호환용 생성자. */
    public RestaurantSummaryResponse(
            Long restaurantId,
            String name,
            String address,
            String status
    ) {
        this(restaurantId, null, "OWNED", name, address, status);
    }

    public static RestaurantSummaryResponse publicRestaurant(
            Long publicRestaurantId,
            String name,
            String address
    ) {
        return new RestaurantSummaryResponse(
                null,
                publicRestaurantId,
                "PUBLIC",
                name,
                address,
                "ACTIVE"
        );
    }
}
