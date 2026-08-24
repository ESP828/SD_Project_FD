package com.example.backend.restaurant.dto.response;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.domain.type.AccountStatus;
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
        boolean isOwner,
        Long ownerAccountId,
        String ownerNickname,
        String ownerProfileImageUrl,
        LocalDateTime createdAt
) {
    public static RestaurantDetailResponse of(
            Restaurant restaurant,
            Double averageRating,
            long reviewCount,
            long favoriteCount,
            long menuCount,
            boolean favoritedByMe,
            boolean isOwner
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
                isOwner,
                ownerAccountId(restaurant.getOwner()),
                displayOwnerNickname(restaurant.getOwner()),
                restaurant.getOwner() != null ? restaurant.getOwner().getProfileImageUrl() : null,
                restaurant.getCreatedAt()
        );
    }

    private static Long ownerAccountId(Account owner) {
        if (owner == null || owner.getStatus() == AccountStatus.WITHDRAWN) {
            return null;
        }
        return owner.getAccountId();
    }

    private static String displayOwnerNickname(Account owner) {
        if (owner == null || owner.getStatus() == AccountStatus.WITHDRAWN) {
            return "탈퇴한 회원";
        }
        return owner.getNickname();
    }
}
