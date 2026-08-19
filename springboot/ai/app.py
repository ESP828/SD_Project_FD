# ai/app.py
from fastapi import FastAPI
from pydantic import BaseModel
from typing import Optional, List
import recommend

app = FastAPI()


class EmbeddingSearchRequest(BaseModel):
    query: str
    restaurantIds: Optional[List[int]] = None
    topK: int = 50


class EmbeddingSearchItem(BaseModel):
    id: int
    score: float


class EmbeddingSearchResponse(BaseModel):
    items: List[EmbeddingSearchItem]


@app.post("/embedding/search", response_model=EmbeddingSearchResponse)
def embedding_search(req: EmbeddingSearchRequest):
    """
    의미 기반 유사도 검색만 담당한다. 위치·거리·카테고리 보너스·개인화 점수 합산은
    Spring Boot(RecommendationService)가 담당하므로 여기서는 순수 임베딩 유사도만 반환한다.
    """
    matched_restaurants = recommend.search_by_sentence_embedding(
        req.query, top_n=req.topK, restaurant_ids=req.restaurantIds
    )

    items = [
        {
            "id": int(place.get("id")),
            "score": float(place.get("query_similarity", 0.0)),
        }
        for place in matched_restaurants
        if place.get("id") is not None
    ]

    return {"items": items}


@app.get("/health")
def health():
    return {
        "status": "ok",
        "modelLoaded": recommend.restaurant_embeddings is not None,
        "restaurantCount": 0 if recommend.restaurants_df is None else len(recommend.restaurants_df),
    }
