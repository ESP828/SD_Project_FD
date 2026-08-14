from fastapi import FastAPI, Depends, HTTPException
from sqlalchemy.orm import Session
from pydantic import BaseModel
from typing import List, Optional
from database import get_db
import recommend

app = FastAPI()

# ------------------------------------------------------------------
# 1. 요청 DTO 정의 (Spring Boot 백엔드와 규격 일치)
# ------------------------------------------------------------------
class PersonalizedRecommendRequest(BaseModel):
    user_id: Optional[int] = None
    age: Optional[int] = None
    gender: Optional[str] = None  # 'M' or 'F'
    latitude: float
    longitude: float
    radius_meters: int = 2000
    limit: int = 30


# ------------------------------------------------------------------
# 2. 개인화 맛집 추천 엔드포인트
# ------------------------------------------------------------------
@app.post("/recommendations/personal")
def recommend_personalized(req: PersonalizedRecommendRequest, db: Session = Depends(get_db)):
    try:
        # A. 유저 ID가 전달된 경우 DB에서 찜 이력(카테고리/식당명 토큰) 조회
        user_bookmarks = []
        if req.user_id:
            user_bookmarks = recommend.get_user_favorite_category_tokens(db, req.user_id)

        # B. 위치(lat/lng) 기반 주변 맛집 후보군 DB 조회
        candidate_restaurants = recommend.get_nearby_restaurants(
            db, req.latitude, req.longitude, req.radius_meters
        )

        result_items = []
        for place in candidate_restaurants:
            # 거리 정보 가져오기 (기본값 처리)
            dist = place.get("distance_meters", 0.0)

            # C. 유저 정보 기반 스코어링 (recommend.py의 함수 호출)
            score, reasons = recommend.calculate_personal_score(
                restaurant=place,
                user_age=req.age,
                user_gender=req.gender,
                user_bookmarks=user_bookmarks,
                distance_meters=dist,
                max_radius=req.radius_meters
            )

            result_items.append({
                "sourceId": str(place.get("id", "")),
                "restaurantName": place.get("name") or place.get("restaurant_name", ""),
                "categoryName": place.get("category_name", "음식점"),
                "address": place.get("address") or place.get("road_address", "주소 정보 없음"),
                "distanceMeters": dist,
                "score": score,
                "reasons": reasons
            })

        # D. 매칭 점수(score) 내림차순 정렬 후 limit 개수만큼 슬라이싱
        result_items.sort(key=lambda x: x["score"], reverse=True)
        final_items = result_items[:req.limit]

        return {
            "totalCount": len(final_items),
            "items": final_items
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
