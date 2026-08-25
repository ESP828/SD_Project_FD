package com.example.backend.recommendation.preference.domain;

/**
 * 취향 프로필을 만들 때 매장 하나가 갖는 영향력.
 * weight는 항상 양수이며, 긍정/부정 구분은 어느 목록에 담기느냐로 표현한다.
 */
public record WeightedRestaurantSignal(Long restaurantId, double weight) {
}
