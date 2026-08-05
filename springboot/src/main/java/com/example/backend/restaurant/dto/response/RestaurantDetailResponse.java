package com.example.backend.restaurant.dto.response;

import com.example.backend.restaurant.domain.entity.Restaurant;

import java.time.LocalDateTime;

/**
 * 우리 사이트에 사업자가 직접 등록한 음식점의 상세 정보.
 * 영업시간·전화번호·메뉴·리뷰·소식은 이 경로(사업자 등록)로만 제공하고,
 * 공공데이터 출처 음식점에는 노출하지 않는다.
 */
public record RestaurantDetailResponse(
        Long restaurantId,
        String name,
        String categoryName,
        String address,
        String addressDetail,
        Double latitude,
        Double longitude,
        String phone,
        String openingHours,
        String closedDays,
        String description,
        String status,
        Double averageRating,
        long reviewCount,
        long favoriteCount,
        long menuCount,
        boolean favoritedByMe,
        LocalDateTime createdAt
) {
    public static RestaurantDetailResponse of(
            Restaurant restaurant,
            Double averageRating,
            long reviewCount,
            long favoriteCount,
            long menuCount,
            boolean favoritedByMe
    ) {
        return new RestaurantDetailResponse(
                restaurant.getRestaurantId(),
                restaurant.getName(),
                restaurant.getCategory() != null ? restaurant.getCategory().getName() : null,
                restaurant.getAddress(),
                restaurant.getAddressDetail(),
                restaurant.getLatitude() == null ? null : restaurant.getLatitude().doubleValue(),
                restaurant.getLongitude() == null ? null : restaurant.getLongitude().doubleValue(),
                restaurant.getPhone(),
                restaurant.getOpeningHours(),
                restaurant.getClosedDays(),
                restaurant.getDescription(),
                restaurant.getStatus().name(),
                averageRating,
                reviewCount,
                favoriteCount,
                menuCount,
                favoritedByMe,
                restaurant.getCreatedAt()
        );
    }
}
