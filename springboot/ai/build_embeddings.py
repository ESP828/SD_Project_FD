import os
import sys
import json
import joblib
import numpy as np
import pandas as pd
from sqlalchemy import create_engine, text
from sklearn.feature_extraction.text import TfidfVectorizer
from sentence_transformers import SentenceTransformer

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DIR = os.path.join(BASE_DIR, 'model')
SPRING_MODEL_DIR = os.path.abspath(os.path.join(BASE_DIR, '..', 'src', 'main', 'resources', 'recommendation', 'model'))
ENV_PATH = os.path.join(BASE_DIR, '..', '.env')

# 💡 recommend.py / app.py가 사용하는 실제 의미 검색용 임베딩 모델
# KURE-v1(고려대 NLP&AI Lab, BGE-M3 한국어 파인튜닝, 1024차원)은 E5 계열과 달리
# query:/passage: 프리픽스 없이 원문을 그대로 인코딩한다.
EMBEDDING_MODEL_NAME = 'nlpai-lab/KURE-v1'
EMBEDDINGS_PATH = os.path.join(OUTPUT_DIR, 'restaurant_embeddings.npy')
RESTAURANTS_META_PATH = os.path.join(OUTPUT_DIR, 'restaurants_meta.pkl')
EMBEDDING_MODEL_MARKER_PATH = os.path.join(OUTPUT_DIR, 'embedding_model.json')

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

    # 💡 ORDER BY로 행 순서를 고정한다 (DB 자체는 변경 없음, 조회 순서만 결정).
    # restaurant_embeddings.npy는 이 순서 그대로 저장되는데, 정렬이 없으면
    # 클라우드로 공유받은 .npy와 팀원 PC에서 새로 조회한 restaurants_meta.pkl의
    # 행 순서가 어긋나 엉뚱한 식당으로 매칭될 수 있다.
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
        ORDER BY public_restaurant_id
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

def build_restaurant_embeddings(force_rebuild_embeddings=False):
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

    # model-meta.json 생성 (💡 RecommendationModelStore.java가 읽는 실제 파일명과 일치시킴)
    meta_info = {
        "modelVersion": "fooduck-tfidf-v2-db-synonyms",
        "vocabularySize": len(vocab_dict),
        "totalDocuments": len(df)
    }
    with open(os.path.join(SPRING_MODEL_DIR, 'model-meta.json'), 'w', encoding='utf-8') as f:
        json.dump(meta_info, f, ensure_ascii=False, indent=2)

    print("\n========================================================")
    print("✅ 스프링부트 TF-IDF 폴백 모델 파일 갱신 완료!")
    print(f"- 단어 사전 크기: {len(vocab_dict):,}개 (1글자 '전', '면' 포함)")
    print(f"- 저장 경로: {SPRING_MODEL_DIR}")
    print("========================================================")

    # 3. Python FastAPI가 서빙할 실제 AI 의미 검색용 임베딩 생성 (기본은 재생성하지 않음)
    build_semantic_embeddings(df, force_rebuild=force_rebuild_embeddings)

def build_semantic_embeddings(df, force_rebuild=False):
    """
    SentenceTransformer로 식당 문서를 의미 벡터로 변환해 recommend.py / app.py가 서빙할
    restaurant_embeddings.npy를 생성한다.

    이 함수는 _서버실행.bat이 매번 자동으로 호출하므로, 기본값으로는 절대 새로
    인코딩하지 않는다 - 캐시가 있고 최신 상태면 그대로 쓰고, 없거나 낡았으면
    "생성하지 않고" 건너뛴다(이 경우 Spring Boot는 TF-IDF로 자동 폴백한다).
    실제로 새로 인코딩하려면 `python build_embeddings.py --rebuild-embeddings`처럼
    명시적으로 실행해야 한다(CPU 환경에서 138,561건 기준 2~3시간 소요).
    """
    if os.path.exists(EMBEDDINGS_PATH) and os.path.exists(EMBEDDING_MODEL_MARKER_PATH):
        try:
            with open(EMBEDDING_MODEL_MARKER_PATH, 'r', encoding='utf-8') as f:
                cached_model_name = json.load(f).get('modelName')
            cached_embeddings = np.load(EMBEDDINGS_PATH, mmap_mode='r')
            if cached_embeddings.shape[0] == len(df) and cached_model_name == EMBEDDING_MODEL_NAME:
                print("\n🧠 [5/5] AI 임베딩이 최신 상태입니다. 재생성을 건너뜁니다.")
                return
        except Exception:
            pass  # 캐시 파일이 손상된 경우 아래에서 판단

    if not force_rebuild:
        print("\n🧠 [5/5] AI 임베딩(.npy)이 없거나 최신 상태가 아닙니다.")
        print("   자동으로 새로 만들지 않습니다 (CPU 환경에서 2~3시간 소요될 수 있음).")
        print("   당장은 TF-IDF 폴백으로 검색이 동작합니다.")
        print("   새로 만들려면: python build_embeddings.py --rebuild-embeddings")
        return

    print("\n🧠 [5/5] AI 임베딩(SentenceTransformer) 생성 중... (--rebuild-embeddings로 직접 요청됨)")
    passages = (
        df['name'] + " " +
        df['category_large_name'] + " " +
        df['category_medium_name'] + " " +
        df['category_small_name'] + " " +
        df['road_address']
    ).tolist()

    embedding_model = SentenceTransformer(EMBEDDING_MODEL_NAME)
    embeddings = embedding_model.encode(
        passages,
        batch_size=32,
        show_progress_bar=True,
        normalize_embeddings=True
    )

    os.makedirs(OUTPUT_DIR, exist_ok=True)
    np.save(EMBEDDINGS_PATH, embeddings)
    df.to_pickle(RESTAURANTS_META_PATH)
    with open(EMBEDDING_MODEL_MARKER_PATH, 'w', encoding='utf-8') as f:
        json.dump({"modelName": EMBEDDING_MODEL_NAME}, f, ensure_ascii=False, indent=2)
    print(f"✅ AI 임베딩 저장 완료: shape={embeddings.shape}, 경로={EMBEDDINGS_PATH}")

if __name__ == "__main__":
    build_restaurant_embeddings(force_rebuild_embeddings="--rebuild-embeddings" in sys.argv)
