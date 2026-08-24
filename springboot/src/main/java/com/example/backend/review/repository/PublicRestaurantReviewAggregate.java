package com.example.backend.review.repository;

/** 맛집 랭킹 계산용. 공공데이터 매장 하나의 활성 리뷰 개수·평균 평점을 한 번에 집계한 결과. */
public record PublicRestaurantReviewAggregate(Long publicRestaurantId, Long reviewCount, Double averageRating) {
}
