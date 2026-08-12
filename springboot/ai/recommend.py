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

    return tokensr
