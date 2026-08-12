package com.example.backend.recommendation.dto.response;

import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse.RecommendedItemDto;

import java.util.List;

public record PersonalRecommendationResponse(
        boolean hasPreferenceData,       // 찜/선호 데이터 존재 여부 (false면 오리 UI 출력)
        String userPreferenceSummary,    // 예: "회원님이 찜한 양식, 파스타 취향 기반"
        List<RecommendedItemDto> items   // 추천 매장 목록
) {}
