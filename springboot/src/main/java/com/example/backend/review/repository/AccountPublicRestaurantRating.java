package com.example.backend.review.repository;

/**
 * 개인화 추천용. 한 사용자가 공공데이터 매장 하나에 남긴 활성 리뷰의 평점만 뽑은 결과.
 * 취향 벡터 계산에는 리뷰 본문이 필요 없으므로 엔티티 대신 이 projection을 쓴다.
 * rating은 Review 엔티티와 같은 byte라서 Byte로 받는다(생성자 타입이 정확히 맞아야 한다).
 */
public record AccountPublicRestaurantRating(Long publicRestaurantId, Byte rating) {

    public int ratingValue() {
        return rating == null ? 0 : rating.intValue();
    }
}
