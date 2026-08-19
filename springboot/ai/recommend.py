# ai/recommend.py 내부
import os
import numpy as np
import pandas as pd
from sentence_transformers import SentenceTransformer

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.join(BASE_DIR, 'model')

# 💡 build_embeddings.py가 생성하는 임베딩과 동일한 모델을 사용해야 벡터 공간이 일치한다.
# KURE-v1(고려대 NLP&AI Lab, BGE-M3 한국어 파인튜닝)은 E5 계열과 달리 query:/passage:
# 프리픽스 없이 원문을 그대로 인코딩한다.
EMBEDDING_MODEL_NAME = 'nlpai-lab/KURE-v1'

# 💡 모델 및 임베딩 데이터는 서버 시작 시 1회만 메모리에 로드
model = SentenceTransformer(EMBEDDING_MODEL_NAME)
embeddings_path = os.path.join(MODEL_DIR, 'restaurant_embeddings.npy')
meta_path = os.path.join(MODEL_DIR, 'restaurants_meta.pkl')

if os.path.exists(embeddings_path) and os.path.exists(meta_path):
    restaurant_embeddings = np.load(embeddings_path)
    restaurants_df = pd.read_pickle(meta_path)
else:
    restaurant_embeddings = None
    restaurants_df = None


def search_by_sentence_embedding(query: str, top_n: int = 50, restaurant_ids=None):
    """
    자연어 검색어와 식당 임베딩 간의 코사인 유사도 검색.
    restaurant_ids가 주어지면(위치 필터를 이미 거친 후보군) 그 안에서만 검색해
    전체 식당을 매번 스캔하지 않는다.
    """
    if restaurant_embeddings is None or not query.strip():
        return []

    # 1. 사용자 질문/검색어를 벡터로 변환 (KURE-v1은 프리픽스 없이 원문을 그대로 인코딩)
    query_vector = model.encode([query], normalize_embeddings=True)

    # 2. 검색 대상 후보 결정 (id 필터가 있으면 그 안에서만, 없으면 전체)
    if restaurant_ids:
        id_set = set(restaurant_ids)
        mask = restaurants_df['id'].isin(id_set).to_numpy()
        candidate_indices = np.where(mask)[0]
        if len(candidate_indices) == 0:
            return []
    else:
        candidate_indices = np.arange(len(restaurants_df))

    # 3. 후보와의 코사인 유사도 내적(dot product) 계산 (임베딩이 정규화되어 있어 내적 = 코사인 유사도)
    candidate_embeddings = restaurant_embeddings[candidate_indices]
    similarities = np.dot(candidate_embeddings, query_vector.T).flatten()

    # 4. 상위 N개 추출
    order = np.argsort(similarities)[::-1][:top_n]
    top_indices = candidate_indices[order]
    top_scores = similarities[order]

    results = []
    for idx, score in zip(top_indices, top_scores):
        item = restaurants_df.iloc[idx].to_dict()
        item['query_similarity'] = float(score)
        results.append(item)

    return results
