import sqlite3

conn = sqlite3.connect("restaurant.db")
cursor = conn.cursor()

cursor.execute("""
CREATE TABLE IF NOT EXISTS restaurant(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT,
    region TEXT,
    category TEXT,
    keyword TEXT,
    rating REAL
)
""")

restaurants = [
    ("미도인", "강남", "파스타", "데이트", 4.8),
    ("오복수산", "강남", "초밥", "혼밥", 4.7),
    ("하이디라오", "홍대", "중식", "모임", 4.9),
    ("애슐리", "홍대", "양식", "가족", 4.5),
    ("정돈", "강남", "돈까스", "혼밥", 4.9)
]

cursor.executemany("""
INSERT INTO restaurant
(name, region, category, keyword, rating)
VALUES (?, ?, ?, ?, ?)
""", restaurants)

conn.commit()
conn.close()

print("DB 생성 완료")
