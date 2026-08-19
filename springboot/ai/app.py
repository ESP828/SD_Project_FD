# ai/app.py
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Optional, List
import recommend
import sentiment

app = FastAPI()

class QueryRecommendationRequest(BaseModel):
    query: str
    latitude: float
    longitude: float
    radius_meters: int = 2000
    user_id: Optional[int] = None
    age: Optional[int] = None
    gender: Optional[str] = None
    limit: int = 120


# ---- 감성분석 (Naive Bayes) ----

class ReviewRequest(BaseModel):
    review: str

class PredictionResponse(BaseModel):
    review: str
    prediction: str
    positive_probability: float
    negative_probability: float

class RestaurantReviewsRequest(BaseModel):
    restaurantId: int
    restaurantName: str
    reviews: List[str]

class RestaurantSentimentSummary(BaseModel):
    restaurantId: int
    restaurantName: str
    reviewCount: int
    positiveCount: int
    negativeCount: int
    positiveRatio: float


@app.post("/predict/sentiment", response_model=PredictionResponse)
def predict_sentiment(request: ReviewRequest):
    if not sentiment.is_ready():
        raise HTTPException(status_code=503, detail="감성분석 모델이 아직 준비되지 않았습니다.")
    return PredictionResponse(**sentiment.predict_one(request.review))


@app.post("/predict/sentiment/restaurant-summary", response_model=RestaurantSentimentSummary)
def predict_sentiment_restaurant_summary(request: RestaurantReviewsRequest):
    if not sentiment.is_ready():
        raise HTTPException(status_code=503, detail="감성분석 모델이 아직 준비되지 않았습니다.")
    return RestaurantSentimentSummary(
        **sentiment.summarize_restaurant(request.restaurantId, request.restaurantName, request.reviews)
    )

@app.post("/recommendations/query")
def recommend_by_query(req: QueryRecommendationRequest):
    # 1. 딥러닝 임베딩 기반 의미 검색 실행
    matched_restaurants = recommend.search_by_sentence_embedding(req.query, top_n=req.limit)

    final_results = []
    for place in matched_restaurants:
        # 거리 계산 (간이 거리 또는 DB 거리)
        dist = place.get("distance_meters", 500)

        # 2. 임베딩 유사도 점수와 개인화 스코어링 합성
        score, reasons = recommend.calculate_personal_score(
            restaurant=place,
            user_age=req.age,
            user_gender=req.gender,
            distance_meters=dist,
            max_radius=req.radius_meters,
            query_similarity=place.get("query_similarity", 0.85) # 👈 임베딩 점수 반영
        )

        final_results.append({
            "sourceId": str(place.get("public_restaurant_id") or place.get("id")),
            "restaurantName": place.get("name"),
            "categoryName": place.get("category_medium_name") or place.get("category_large_name", "음식점"),
            "address": place.get("road_address", ""),
            "distanceMeters": dist,
            "score": score,
            "reasons": reasons
        })

    # 최종 점수순 정렬
    final_results.sort(key=lambda x: x["score"], reverse=True)

    return {
        "originalQuery": req.query,
        "items": final_results
    }
