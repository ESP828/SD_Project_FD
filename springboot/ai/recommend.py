# ai/recommend.py 내부 찜 데이터 조회 부분
def get_user_favorite_category_tokens(db_session, user_id: int):
    query = """
        SELECT COALESCE(pr.category_medium_name, pr.category_large_name, rc.name) AS category_name,
               COALESCE(pr.name, r.name) AS restaurant_name
        FROM favorite f
        LEFT JOIN restaurant r ON r.restaurant_id = f.restaurant_id
        LEFT JOIN public_restaurant pr ON pr.public_restaurant_id = f.public_restaurant_id
        LEFT JOIN restaurant_category rc ON rc.category_id = r.category_id
        WHERE f.account_id = :user_id
    """
    result = db_session.execute(text(query), {"user_id": user_id}).fetchall()

    tokens = []
    for row in result:
        if row.category_name:
            tokens.append(row.category_name)
        if row.restaurant_name:
            tokens.append(row.restaurant_name)

    return tokens

# ai/recommend.py 내부 찜 데이터 조회 부분
def get_user_favorite_category_tokens(db_session, user_id: int):
    query = """
        SELECT COALESCE(pr.category_medium_name, pr.category_large_name, rc.name) AS category_name,
               COALESCE(pr.name, r.name) AS restaurant_name
        FROM favorite f
        LEFT JOIN restaurant r ON r.restaurant_id = f.restaurant_id
        LEFT JOIN public_restaurant pr ON pr.public_restaurant_id = f.public_restaurant_id
        LEFT JOIN restaurant_category rc ON rc.category_id = r.category_id
        WHERE f.account_id = :user_id
    """
    result = db_session.execute(text(query), {"user_id": user_id}).fetchall()

    tokens = []
    for row in result:
        if row.category_name:
            tokens.append(row.category_name)
        if row.restaurant_name:
            tokens.append(row.restaurant_name)

    return tokens

def calculate_personal_score(restaurant, user_age, user_gender, user_bookmarks, distance_meters, max_radius=2000):
    """
    유저 정보 기반 점수 계산 (0.0 ~ 1.0)
    """
    base_score = 0.5
    reasons = []
    category = restaurant.get("category_name", "")

    # 1. 찜(즐겨찾기) 이력 반영 (가장 높은 가중치: +0.3)
    if user_bookmarks and category in user_bookmarks:
        base_score += 0.3
        reasons.append("자주 찜한 취향 맛집")

    # 2. 연령대별 선호도 반영 (+0.1)
    if user_age:
        age_group = (user_age // 10) * 10  # 20대, 30대 등
        if age_group == 20 and category in ["카페", "디저트", "양식", "패스트푸드"]:
            base_score += 0.1
            reasons.append(f"{age_group}대 인기 스팟")
        elif age_group in [30, 40] and category in ["한식", "일식", "중식"]:
            base_score += 0.1
            reasons.append(f"{age_group}대 선호 스팟")

    # 3. 성별 선호도 반영 (+0.05)
    if user_gender:
        if user_gender == 'F' and category in ["카페", "디저트", "양식"]:
            base_score += 0.05
        elif user_gender == 'M' and category in ["한식", "국밥", "고기", "주점"]:
            base_score += 0.05

    # 4. 거리 감쇄 점수 (거리가 가까울수록 점수 가산)
    distance_score = max(0, 1.0 - (distance_meters / max_radius))

    # 최종 점수 = 개인화 점수 70% + 거리 점수 30%
    final_score = min((base_score * 0.7) + (distance_score * 0.3), 1.0)

    # 뱃지가 없을 때 기본 뱃지
    if not reasons:
        reasons.append("주변 추천 맛집")

    return round(final_score, 2), reasons

