package com.example.backend.map.dto.response;

import com.example.backend.restaurant.domain.entity.PublicRestaurant;

/**
 * 공공데이터 출처 음식점의 상세 정보. 상호명·업종·주소·좌표만 제공한다
 * (영업시간·전화번호·메뉴·리뷰는 이 데이터셋에 존재하지 않는다).
 */
public record PublicRestaurantDetailResponse(
        Long id,
        String name,
        String branchName,
        String categoryLargeName,
        String categorySmallName,
        String roadAddress,
        String lotAddress,
        Double lat,
        Double lon,
        String dataYm,
        long favoriteCount,
        boolean favoritedByMe
) {
    public static PublicRestaurantDetailResponse from(PublicRestaurant restaurant, long favoriteCount, boolean favoritedByMe) {
        return new PublicRestaurantDetailResponse(
                restaurant.getPublicRestaurantId(),
                restaurant.getName(),
                restaurant.getBranchName(),
                restaurant.getCategoryLargeName(),
                restaurant.getCategorySmallName(),
                restaurant.getRoadAddress(),
                restaurant.getLotAddress(),
                restaurant.getLatitude() == null ? null : restaurant.getLatitude().doubleValue(),
                restaurant.getLongitude() == null ? null : restaurant.getLongitude().doubleValue(),
                restaurant.getDataYm(),
                favoriteCount,
                favoritedByMe
        );
    }
}
