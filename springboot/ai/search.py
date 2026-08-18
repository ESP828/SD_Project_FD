import sqlite3

def search_restaurant(info: dict, page: int = 0, page_size: int = 12):
    """
    개선된 키워드 구조를 바탕으로 유연한 다중 조건 검색 및 페이징을 지원합니다.
    """
    conn = sqlite3.connect("restaurant.db")
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()

    region = info.get("region")
    category = info.get("category")
    keywords = info.get("keywords", [])
    # 단일 keyword 문자열로 들어온 경우도 호환 처리
    if isinstance(info.get("keyword"), str) and info.get("keyword"):
        keywords.extend(info["keyword"].split())

    # 1. 메인 쿼리 조립 (모든 조건 AND 매칭)
    sql = "SELECT * FROM restaurant WHERE 1=1"
    params = []

    if region:
        sql += " AND region LIKE ?"
        params.append(f"%{region.strip()}%")

    if category:
        sql += " AND category LIKE ?"
        params.append(f"%{category.strip()}%")

    for kw in set(keywords):
        if kw:
            sql += " AND (name LIKE ? OR category LIKE ? OR keyword LIKE ?)"
            wildcard = f"%{kw.strip()}%"
            params.extend([wildcard, wildcard, wildcard])

    # 페이징 적용
    offset = page * page_size
    paged_sql = sql + " LIMIT ? OFFSET ?"
    paged_params = params + [page_size, offset]

    cursor.execute(paged_sql, paged_params)
    rows = cursor.fetchall()
    results = [dict(row) for row in rows]

    # 2. 검색 결과가 0건이고 키워드가 있었던 경우 -> Fallback (지역/카테고리만으로 완화 검색)
    if not results and keywords and (region or category):
        fallback_sql = "SELECT * FROM restaurant WHERE 1=1"
        fallback_params = []
        if region:
            fallback_sql += " AND region LIKE ?"
            fallback_params.append(f"%{region.strip()}%")
        if category:
            fallback_sql += " AND category LIKE ?"
            fallback_params.append(f"%{category.strip()}%")

        fallback_sql += " LIMIT ? OFFSET ?"
        fallback_params.extend([page_size, offset])
        cursor.execute(fallback_sql, fallback_params)
        results = [dict(row) for row in cursor.fetchall()]

    conn.close()
    return results
