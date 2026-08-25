package com.example.backend.recommendation.dto.response;

import java.util.List;

/**
 * 「나를 위한 맛집」 응답.
 *
 * <p>어떤 재료로 만든 추천인지를 personalizationLevel로 알려준다.
 * FULL(찜+프로필) / BEHAVIOR_ONLY(찜만) /
 * NO_FAVORITES(찜 없음) / ANONYMOUS(비로그인).
 */
public record PersonalRecommendationResponse(
        String personalizationLevel,
        String userPreferenceSummary,
        List<PersonalRecommendedItemDto> items
) {}
