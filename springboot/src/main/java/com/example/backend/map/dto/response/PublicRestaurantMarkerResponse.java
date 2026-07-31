package com.example.backend.map.dto.response;

import com.example.backend.restaurant.domain.entity.PublicRestaurant;

public record PublicRestaurantMarkerResponse(
        Long id,
        String name,
        String categoryName,
        String roadAddress,
        String lotAddress,
        Double lat,
        Double lon
) {
    public static PublicRestaurantMarkerResponse from(PublicRestaurant restaurant) {
        String categoryName = restaurant.getCategorySmallName() != null
                ? restaurant.getCategorySmallName()
                : restaurant.getCategoryMediumName();
        return new PublicRestaurantMarkerResponse(
                restaurant.getPublicRestaurantId(),
                restaurant.getName(),
                categoryName,
                restaurant.getRoadAddress(),
                restaurant.getLotAddress(),
                restaurant.getLatitude() == null ? null : restaurant.getLatitude().doubleValue(),
                restaurant.getLongitude() == null ? null : restaurant.getLongitude().doubleValue()
        );
    }
}
