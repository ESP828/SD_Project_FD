# ai/app.py
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Optional, List
import recommend
import sentiment

app = FastAPI()

class EmbeddingSearchRequest(BaseModel):
    query: str
    restaurantIds: List[int] = []
    topK: int = 50


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

class RestaurantReviewsBatchRequest(BaseModel):
    items: List[RestaurantReviewsRequest]

class RestaurantSentimentSummaryBatch(BaseModel):
    items: List[RestaurantSentimentSummary]


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

@app.post("/predict/sentiment/restaurant-summary/batch", response_model=RestaurantSentimentSummaryBatch)
def predict_sentiment_restaurant_summary_batch(request: RestaurantReviewsBatchRequest):
    # 맛집 랭킹 화면처럼 매장 여러 곳을 한 번에 다뤄야 할 때, 매장마다 따로 HTTP 호출하지
    # 않고 한 번의 요청으로 처리한다. 리뷰가 없는 매장은 건너뛴다.
    if not sentiment.is_ready():
        raise HTTPException(status_code=503, detail="감성분석 모델이 아직 준비되지 않았습니다.")
    results = []
    for item in request.items:
        if not item.reviews:
            continue
        results.append(RestaurantSentimentSummary(
            **sentiment.summarize_restaurant(item.restaurantId, item.restaurantName, item.reviews)
        ))
    return RestaurantSentimentSummaryBatch(items=results)


@app.post("/embedding/search")
def embedding_search(request: EmbeddingSearchRequest):
    # RecommendationService가 위치/카테고리 필터를 이미 거친 후보군(restaurantIds) 안에서만
    # KURE 임베딩 코사인 유사도를 계산해 순위를 매긴다. 임베딩(.npy)이 아직 없으면
    # recommend.search_by_sentence_embedding이 빈 리스트를 반환하고, Java 쪽은 TF-IDF로 폴백한다.
    results = recommend.search_by_sentence_embedding(
        request.query, top_n=request.topK, restaurant_ids=request.restaurantIds
    )
    items = [
        {"id": int(r["id"]), "score": float(r["query_similarity"])}
        for r in results
    ]
    return {"items": items}


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
