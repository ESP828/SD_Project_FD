package com.example.backend.review.integration.sentiment.dto;

import java.util.List;

public record RestaurantSentimentSummaryRequest(
        Long restaurantId,
        String restaurantName,
        List<ReviewItem> reviews
) {
    // 리뷰 본문만으로는 감성 판단이 안 되는 경우(사전에 없는 단어뿐이거나 "ㄹㅇㅎ" 같은 무의미한
    // 입력)를 대비해 별점을 같이 보낸다 - FastAPI 쪽에서 본문 인식이 안 되면 별점으로 대신 판단한다.
    public record ReviewItem(String content, int rating) {
    }
}
