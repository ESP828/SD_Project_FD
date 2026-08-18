# ai/recommend.py 내부
import os
import numpy as np
import pandas as pd
from sklearn.metrics.pairwise import cosine_similarity
from sentence_transformers import SentenceTransformer

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.join(BASE_DIR, 'model')

# 💡 모델 및 임베딩 데이터는 서버 시작 시 1회만 메모리에 로드
model = SentenceTransformer('jhgan/ko-sroberta-multitask')
embeddings_path = os.path.join(MODEL_DIR, 'restaurant_embeddings.npy')
meta_path = os.path.join(MODEL_DIR, 'restaurants_meta.pkl')

if os.path.exists(embeddings_path) and os.path.exists(meta_path):
    restaurant_embeddings = np.load(embeddings_path)
    restaurants_df = pd.read_pickle(meta_path)
else:
    restaurant_embeddings = None
    restaurants_df = None


def search_by_sentence_embedding(query: str, top_n: int = 50):
    """
    자연어 검색어와 식당 임베딩 간의 코사인 유사도 검색
    """
    if restaurant_embeddings is None or not query.strip():
        return []

    # 1. 사용자 질문/검색어를 벡터로 변환 (normalize_embeddings=True로 코사인 유사도 계산 간소화)
    query_vector = model.encode([query], normalize_embeddings=True)

    # 2. 전체 식당과의 코사인 유사도 내적(dot product) 계산
    similarities = np.dot(restaurant_embeddings, query_vector.T).flatten()

    # 3. 상위 N개 추출
    top_indices = np.argsort(similarities)[::-1][:top_n]

    results = []
    for idx in top_indices:
        item = restaurants_df.iloc[idx].to_dict()
        item['query_similarity'] = float(similarities[idx])
        results.append(item)

    return results
