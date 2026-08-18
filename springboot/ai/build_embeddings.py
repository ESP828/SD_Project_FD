import os
import json
import joblib
import pandas as pd
from sqlalchemy import create_engine, text
from sklearn.feature_extraction.text import TfidfVectorizer

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DIR = os.path.join(BASE_DIR, 'model')
SPRING_MODEL_DIR = os.path.abspath(os.path.join(BASE_DIR, '..', 'src', 'main', 'resources', 'recommendation', 'model'))
ENV_PATH = os.path.join(BASE_DIR, '..', '.env')

# 💡 [정답지] 주요 음식 및 연관 검색어
FOOD_SYNONYMS = {
    "전": ["파전", "해물파전", "김치전", "감자전", "녹두전", "빈대떡", "부침개", "지짐이", "민속주점", "주막", "막걸리"],
    "파전": ["해물파전", "전", "빈대떡", "부침개", "민속주점", "막걸리"],
    "비": ["비오는날", "파전", "김치전", "수제비", "칼국수", "막걸리", "전"],
    "면": ["칼국수", "라멘", "우동", "짜장면", "짬뽕", "파스타", "소바", "냉면", "국수"],
    "해장": ["국밥", "순대국", "뼈해장국", "황태해장국", "콩나물국밥", "라면", "짬뽕"],
    "고기": ["삼겹살", "돼지갈비", "소고기", "한우", "구이", "생고기", "목살"]
}

def load_env_credentials():
    creds = {
        "DB_HOST": "192.168.1.185",
        "DB_PORT": "3306",
        "DB_NAME": "foodduck",
        "DB_USER": "foodduck",
        "DB_PASS": "1234"
    }
    if os.path.exists(ENV_PATH):
        with open(ENV_PATH, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    k, v = k.strip(), v.strip().strip("'").strip('"')
                    if k in ["DB_USERNAME", "SPRING_DATASOURCE_USERNAME", "MYSQL_USER"]: creds["DB_USER"] = v
                    elif k in ["DB_PASSWORD", "SPRING_DATASOURCE_PASSWORD", "MYSQL_PASSWORD"]: creds["DB_PASS"] = v
                    elif k in ["DB_HOST", "MYSQL_HOST"]: creds["DB_HOST"] = v
                    elif k in ["DB_PORT", "MYSQL_PORT"]: creds["DB_PORT"] = v
                    elif k in ["DB_NAME", "MYSQL_DATABASE"]: creds["DB_NAME"] = v
    return creds

def fetch_restaurants_from_db():
    print("========================================================")
    print("🚀 [1/4] MySQL DB에서 식당 데이터 조회 중...")
    print("========================================================")
    creds = load_env_credentials()
    db_url = f"mysql+pymysql://{creds['DB_USER']}:{creds['DB_PASS']}@{creds['DB_HOST']}:{creds['DB_PORT']}/{creds['DB_NAME']}?charset=utf8mb4"
    engine = create_engine(db_url)

    query = """
        SELECT
            public_restaurant_id AS id,
            name,
            category_large_name,
            category_medium_name,
            category_small_name,
            road_address,
            lot_address,
            latitude,
            longitude
        FROM public_restaurant
    """
    with engine.connect() as conn:
        df = pd.read_sql(text(query), conn)
    return df.fillna('')

def enrich_text_with_synonyms(text_val):
    extra = []
    for key, syns in FOOD_SYNONYMS.items():
        if key in text_val:
            extra.extend(syns)
        for s in syns:
            if s in text_val:
                extra.append(key)
                extra.extend(syns[:3])
                break
    return text_val + " " + " ".join(set(extra))

def build_restaurant_embeddings():
    df = fetch_restaurants_from_db()
    if len(df) == 0:
        print("[WARN] 식당 데이터가 없습니다.")
        return

    print(f"- DB에서 로드된 총 식당 개수: {len(df):,}개")

    print("\n📝 [2/4] 정답지 사전 기반 코퍼스 확장 중...")
    base_corpus = (
        df['name'] + " " +
        df['category_large_name'] + " " +
        df['category_medium_name'] + " " +
        df['category_small_name'] + " " +
        df['road_address'] + " " +
        df['lot_address']
    ).tolist()

    enriched_corpus = [enrich_text_with_synonyms(t) for t in base_corpus]

    print("\n🤖 [3/4] 1글자 토큰 포함 TF-IDF 벡터화 진행 중...")
    vectorizer = TfidfVectorizer(
        token_pattern=r'(?u)\b\w+\b',
        ngram_range=(1, 2),
        max_features=50000,
        sublinear_tf=True
    )
    embeddings = vectorizer.fit_transform(enriched_corpus)

    # 1. 파이썬 모델 파일 저장
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    joblib.dump(embeddings, os.path.join(OUTPUT_DIR, 'restaurant_embeddings.joblib'))
    joblib.dump(vectorizer, os.path.join(OUTPUT_DIR, 'vectorizer.joblib'))
    df.to_pickle(os.path.join(OUTPUT_DIR, 'restaurants_meta.pkl'))

    # 2. 💡 Spring Boot 자바 규격에 맞춰 JSON 파일 생성 (vocabulary: Map, idf: List<Double>)
    print("\n📦 [4/4] Spring Boot 자바 모델 파일(JSON 규격 일치) 동기화 생성 중...")
    os.makedirs(SPRING_MODEL_DIR, exist_ok=True)

    # 단어 인덱스 순서대로 정렬하여 IDF 리스트를 생성
    vocab_dict = {word: int(idx) for word, idx in vectorizer.vocabulary_.items()}

    # 0번 인덱스부터 마지막 인덱스까지 순서대로 IDF 값을 배열(List)로 정렬
    sorted_words = sorted(vocab_dict.items(), key=lambda item: item[1])
    idf_list = [float(vectorizer.idf_[idx]) for _, idx in sorted_words]

    # vocabulary.json (Map 형태 {"단어": 인덱스})
    with open(os.path.join(SPRING_MODEL_DIR, 'vocabulary.json'), 'w', encoding='utf-8') as f:
        json.dump(vocab_dict, f, ensure_ascii=False, indent=2)

    # idf.json (💡 List 형태 [1.2, 0.8, ...])
    with open(os.path.join(SPRING_MODEL_DIR, 'idf.json'), 'w', encoding='utf-8') as f:
        json.dump(idf_list, f, ensure_ascii=False, indent=2)

    # metadata.json 생성
    meta_info = {
        "modelVersion": "fooduck-tfidf-v2-db-synonyms",
        "vocabularySize": len(vocab_dict),
        "totalDocuments": len(df)
    }
    with open(os.path.join(SPRING_MODEL_DIR, 'metadata.json'), 'w', encoding='utf-8') as f:
        json.dump(meta_info, f, ensure_ascii=False, indent=2)

    print("\n========================================================")
    print("✅ 스프링부트 모델 파일 규격 일치 갱신 완료!")
    print(f"- 단어 사전 크기: {len(vocab_dict):,}개 (1글자 '전', '면' 포함)")
    print(f"- 저장 경로: {SPRING_MODEL_DIR}")
    print("========================================================")

if __name__ == "__main__":
    build_restaurant_embeddings()
