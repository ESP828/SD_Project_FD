import sqlite3


def search_restaurant(info):

    conn = sqlite3.connect("restaurant.db")
    cursor = conn.cursor()

    sql = """
    SELECT *
    FROM restaurant
    WHERE 1=1
    """

    params = []

    if info["region"]:
        sql += " AND region=?"
        params.append(info["region"])

    if info["category"]:
        sql += " AND category=?"
        params.append(info["category"])

    if info["keyword"]:
        sql += " AND keyword=?"
        params.append(info["keyword"])

    cursor.execute(sql, params)

    result = cursor.fetchall()

    conn.close()

    return result
