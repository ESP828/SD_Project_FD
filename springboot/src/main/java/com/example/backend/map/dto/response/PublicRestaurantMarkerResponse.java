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
        // 세부 항목("경양식") 대신 정리된 대분류("양식")를 화면에 보여준다.
        String categoryName = restaurant.getCategoryMediumName() != null
                ? restaurant.getCategoryMediumName()
                : restaurant.getCategorySmallName() != null
                        ? restaurant.getCategorySmallName()
                        : restaurant.getCategoryLargeName();
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
