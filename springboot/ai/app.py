import asyncio
from contextlib import asynccontextmanager
from typing import List, Optional

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

import recommend
import sentiment


_initialization_future = None
MAX_CANDIDATE_RESTAURANTS = 10_000

# 「나를 위한 맛집」은 자연어 검색과 달리 상위 N건을 미리 고르는 단계가 없다.
# 반경 안 매장을 전부 채점해야 하므로(강남 3km 기준 약 13,500건) 별도의 상한을 둔다.
# Spring의 recommendation.personal-candidate-limit 상한과 같은 값이어야 한다.
# 이 값이 더 작으면 밀집 지역에서 매 요청이 422로 거절되어 조용히 TF-IDF로 떨어진다.
MAX_PROFILE_CANDIDATE_RESTAURANTS = 20_000


@asynccontextmanager
async def lifespan(_app: FastAPI):
    global _initialization_future
    loop = asyncio.get_running_loop()
    _initialization_future = loop.run_in_executor(None, recommend.initialize)
    yield


app = FastAPI(lifespan=lifespan)


class EmbeddingSearchRequest(BaseModel):
    query: str = Field(min_length=1, max_length=500)
    restaurantIds: List[int] = Field(
        default_factory=list,
        max_length=MAX_CANDIDATE_RESTAURANTS,
    )
    topK: int = Field(default=300, ge=1, le=MAX_CANDIDATE_RESTAURANTS)


class FavoriteScoreRequest(BaseModel):
    favoriteRestaurantIds: List[int] = Field(min_length=1, max_length=500)
    candidateRestaurantIds: List[int] = Field(
        min_length=1,
        max_length=MAX_CANDIDATE_RESTAURANTS,
    )


class WeightedRestaurantSignal(BaseModel):
    restaurantId: int
    weight: float = Field(gt=0)


class PersonalProfileScoreRequest(BaseModel):
    positiveSignals: List[WeightedRestaurantSignal] = Field(min_length=1, max_length=500)
    negativeSignals: List[WeightedRestaurantSignal] = Field(default_factory=list, max_length=500)
    candidateRestaurantIds: List[int] = Field(
        min_length=1,
        max_length=MAX_PROFILE_CANDIDATE_RESTAURANTS,
    )


class ReviewRequest(BaseModel):
    review: str
    rating: Optional[int] = None


class PredictionResponse(BaseModel):
    review: str
    prediction: str
    positive_probability: float
    negative_probability: float


class ReviewItem(BaseModel):
    content: str
    rating: Optional[int] = None


class RestaurantReviewsRequest(BaseModel):
    restaurantId: int
    restaurantName: str
    reviews: List[ReviewItem]


class RestaurantSentimentSummary(BaseModel):
    restaurantId: int
    restaurantName: str
    reviewCount: int
    positiveCount: int
    negativeCount: int
    positiveRatio: float


@app.exception_handler(recommend.KureServiceError)
async def handle_kure_service_error(_request: Request, error: recommend.KureServiceError):
    return JSONResponse(
        status_code=error.status_code,
        content={"code": error.code, "message": error.message},
    )


@app.get("/embedding/health")
def embedding_health():
    return recommend.health()


@app.post("/embedding/search")
def embedding_search(request: EmbeddingSearchRequest):
    return recommend.search(request.query, request.restaurantIds, request.topK)


@app.post("/embedding/favorites")
def embedding_favorites(request: FavoriteScoreRequest):
    return recommend.score_favorites(
        request.favoriteRestaurantIds,
        request.candidateRestaurantIds,
    )


@app.post("/embedding/profile")
def embedding_profile(request: PersonalProfileScoreRequest):
    """찜 + 내가 남긴 평점을 가중치로 합친 개인 취향 프로필 기반 후보 점수.

    /embedding/favorites 는 기존 호출부 호환을 위해 그대로 둔다.
    """
    return recommend.score_profile(
        [signal.model_dump() for signal in request.positiveSignals],
        [signal.model_dump() for signal in request.negativeSignals],
        request.candidateRestaurantIds,
    )


@app.post("/predict/sentiment", response_model=PredictionResponse)
def predict_sentiment(request: ReviewRequest):
    if not sentiment.is_ready():
        return JSONResponse(
            status_code=503,
            content={"code": "SENTIMENT_MODEL_NOT_READY", "message": "Sentiment model is not ready."},
        )
    return PredictionResponse(**sentiment.predict_one(request.review, request.rating))


@app.post("/predict/sentiment/restaurant-summary", response_model=RestaurantSentimentSummary)
def predict_sentiment_restaurant_summary(request: RestaurantReviewsRequest):
    if not sentiment.is_ready():
        return JSONResponse(
            status_code=503,
            content={"code": "SENTIMENT_MODEL_NOT_READY", "message": "Sentiment model is not ready."},
        )
    reviews = [{"content": item.content, "rating": item.rating} for item in request.reviews]
    return RestaurantSentimentSummary(
        **sentiment.summarize_restaurant(request.restaurantId, request.restaurantName, reviews)
    )
