package com.example.backend.recommendation.dto.response;

public record RestaurantRankResponse(
        Long restaurantId,
        String name,
        String category,
        String address,
        Double rawRating,
        Integer reviewCount,
        Integer favoriteCount,
        Double adjustedRatingScore,
        Double finalRankScore,
        Double distanceMeters,
        // AI(Naive Bayes) 리뷰 감성분석 긍정 비율(0~100). 리뷰가 없거나 감성분석 서비스를
        // 못 불렀으면 null - 프론트에서는 이때 "AI 분석 준비 중" 등으로 처리한다.
        Double positiveRatio,
        // 리뷰가 있는 매장에 한해 카카오 이미지 검색으로 캐싱한 대표 이미지. 없으면 null -
        // 프론트에서는 이때 카테고리 마커 아이콘으로 대체한다.
        String imageUrl
) {}
