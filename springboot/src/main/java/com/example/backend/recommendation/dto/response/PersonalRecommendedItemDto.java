package com.example.backend.recommendation.dto.response;

import java.util.List;

/**
 * 「나를 위한 맛집」 카드 하나.
 *
 * <p>자연어 추천과 달리 점수 구성을 그대로 내려준다. 화면에 전부 표시할 필요는 없지만,
 * "왜 이 매장이 위에 있는가"를 API 응답만 보고 확인할 수 있어야 튜닝이 가능하다.
 * 사용되지 않은 신호는 null로 남으며, 그 신호의 추천 사유도 표시하지 않는다.
 * distanceScore는 API 호환을 위해 유지하지만, 거리를 개인화 점수에서 제외하므로 null이다.
 */
public record PersonalRecommendedItemDto(
        Long restaurantId,
        String restaurantName,
        String categoryName,
        String address,
        Double latitude,
        Double longitude,
        Double distanceMeters,
        Double score,
        Double tasteScore,
        Double qualityScore,
        Double demographicScore,
        Double distanceScore,
        Double averageRating,
        Long reviewCount,
        List<String> reasons,
        String imageUrl
) {
}
