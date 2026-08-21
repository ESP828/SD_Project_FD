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


# ---- "식사 vs 카페" 개념을 KURE 임베딩으로 판단하기 (하드코딩 키워드 매칭 대신) ----
# "점심에 먹을만한 곳"처럼 구체적 음식 키워드 없는 대화체 질의는 실질 매치 신호가 없어
# 카페/디저트로 새기 쉽다. "점심"/"카페" 같은 단어를 코드에서 직접 찾는 대신, 검색어와
# 식당 카테고리를 각각 "식사" 개념 문장/"카페" 개념 문장과의 의미 유사도로 비교해
# 자연어 표현이 달라져도(예: "밥약속", "허기질 때") 모델이 알아서 식사 의도를 감지하게 한다.
# 서로 대비되는 개념 두 묶음을 (긍정 축 이름, [문장들]) / (부정 축 이름, [문장들])으로
# 정의해두면, _concept_axis_score가 "이 벡터는 어느 쪽에 더 가까운가"를 계산해준다.
# 새로운 대비 축이 필요해지면(예: "1인 vs 단체") 여기에 한 쌍만 추가하면 된다.
_CONCEPT_AXES = {
    "meal_vs_cafe": (
        # 든든한 식사 자리
        [
            "든든한 한 끼 식사를 할 수 있는 곳",
            "점심 식사 하기 좋은 음식점",
            "저녁 식사로 좋은 밥집",
            "배불리 먹을 수 있는 식당",
        ],
        # 커피/디저트 위주의 카페 자리
        [
            "커피와 디저트를 즐기는 카페",
            "브런치와 베이커리가 있는 카페",
            "차 한 잔 마시며 쉬는 곳",
        ],
    ),
    "lunch_vs_bar": (
        # 낮 시간대에 어울리는 식사 자리
        [
            "가볍게 점심 식사를 할 수 있는 음식점",
            "낮에 편하게 밥 먹기 좋은 식당",
            "점심시간에 들르기 좋은 밥집",
        ],
        # 술자리/저녁 회식 위주의 자리
        [
            "술을 마시며 안주를 즐기는 술집",
            "저녁에 친구들과 한잔하기 좋은 호프집",
            "포장마차에서 소주 한잔하기 좋은 곳",
        ],
    ),
}
_axis_vector_cache = {}
_category_axis_fit_cache = {}

# 각 개념 축이 최종 검색 점수에 얼마나 영향을 주는지. 실제 텍스트 의미 유사도를
# 압도하지 않을 정도로 작게 둔다.
_AXIS_WEIGHTS = {
    "meal_vs_cafe": 0.15,
    "lunch_vs_bar": 0.12,
}


def _normalize(vector):
    norm = np.linalg.norm(vector)
    return vector / norm if norm > 0 else vector


def _get_axis_vectors(axis_name: str):
    if axis_name not in _axis_vector_cache:
        positive_sentences, negative_sentences = _CONCEPT_AXES[axis_name]
        positive_embeds = model.encode(positive_sentences, normalize_embeddings=True)
        negative_embeds = model.encode(negative_sentences, normalize_embeddings=True)
        _axis_vector_cache[axis_name] = (
            _normalize(positive_embeds.mean(axis=0)),
            _normalize(negative_embeds.mean(axis=0)),
        )
    return _axis_vector_cache[axis_name]


def _concept_axis_score(axis_name: str, vector) -> float:
    """벡터가 축의 긍정/부정 개념 중 어디에 가까운지. 양수=긍정 쪽, 음수=부정 쪽 성향."""
    positive_vec, negative_vec = _get_axis_vectors(axis_name)
    return float(np.dot(vector, positive_vec) - np.dot(vector, negative_vec))


def _category_axis_fit(axis_name: str, category_text: str) -> float:
    """카테고리 문자열이 해당 개념 축의 어느 쪽에 가까운지. 카테고리 종류는 수가 적어
    최초 계산 후 캐싱한다."""
    if not category_text:
        return 0.0
    cache_key = (axis_name, category_text)
    if cache_key not in _category_axis_fit_cache:
        category_vector = model.encode([category_text], normalize_embeddings=True)[0]
        _category_axis_fit_cache[cache_key] = _concept_axis_score(axis_name, category_vector)
    return _category_axis_fit_cache[cache_key]


def search_by_sentence_embedding(query: str, top_n: int = 50, restaurant_ids=None):
    """
    자연어 검색어와 식당 임베딩 간의 코사인 유사도 검색.
    restaurant_ids가 주어지면(위치 필터를 이미 거친 후보군) 그 안에서만 검색해
    전체 식당을 매번 스캔하지 않는다.
    """
    if restaurant_embeddings is None or not query.strip():
        return []

    # 1. 사용자 질문/검색어를 벡터로 변환 (KURE-v1은 프리픽스 없이 원문을 그대로 인코딩)
    query_vector = model.encode([query], normalize_embeddings=True)[0]

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

    # 4. 검색어가 각 개념 축(식사 vs 카페, 점심 vs 술자리 등)의 어느 쪽에 가까운지를
    # 반영해 카테고리별로 점수를 보정한다. 질의가 해당 축에 중립적이면(축 점수가 0에
    # 가까우면) 보정도 거의 0이 되어 원래 의미 유사도 순위가 그대로 유지된다.
    candidate_categories = None
    for axis_name in _CONCEPT_AXES:
        query_axis_score = _concept_axis_score(axis_name, query_vector)
        if abs(query_axis_score) <= 1e-6:
            continue
        if candidate_categories is None:
            candidate_categories = (
                restaurants_df['category_small_name'].fillna('')
                .where(restaurants_df['category_small_name'].fillna('') != '', restaurants_df['category_large_name'].fillna(''))
            ).to_numpy()[candidate_indices]
        category_fit = np.array([_category_axis_fit(axis_name, cat) for cat in candidate_categories])
        similarities = similarities + _AXIS_WEIGHTS[axis_name] * query_axis_score * category_fit

    # 5. 상위 N개 추출
    order = np.argsort(similarities)[::-1][:top_n]
    top_indices = candidate_indices[order]
    top_scores = similarities[order]

    results = []
    for idx, score in zip(top_indices, top_scores):
        item = restaurants_df.iloc[idx].to_dict()
        item['query_similarity'] = float(score)
        results.append(item)

    return results
