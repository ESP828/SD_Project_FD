# ai/sentiment.py
"""
train_sentiment.py로 학습해 둔 Naive Bayes 감성분석 모델을 로드하고,
리뷰 텍스트(들)를 긍정/부정으로 분류하는 함수를 제공한다.

recommend.py와 동일한 패턴으로, 모델은 모듈 임포트 시(=서버 기동 시) 1회만 로드한다.
"""
import os
import joblib

# tfidf_vectorizer.pkl은 preprocessor로 text_normalize.normalize_text를 참조하고 있어서,
# 이 모듈을 임포트할 수 있어야 joblib.load()가 정상적으로 역직렬화된다.
import text_normalize  # noqa: F401

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.join(BASE_DIR, "model")

_model_path = os.path.join(MODEL_DIR, "naive_bayes_model.pkl")
_vectorizer_path = os.path.join(MODEL_DIR, "tfidf_vectorizer.pkl")

if os.path.exists(_model_path) and os.path.exists(_vectorizer_path):
    model = joblib.load(_model_path)
    vectorizer = joblib.load(_vectorizer_path)
else:
    # train_sentiment.py를 아직 실행하지 않은 경우. app.py가 뜨는 것 자체는 막지 않고,
    # 예측 시점에 명확한 에러를 낸다.
    model = None
    vectorizer = None


def is_ready() -> bool:
    return model is not None and vectorizer is not None


def predict_one(review: str, rating: int | None = None) -> dict:
    """단일 리뷰 -> {"review", "prediction", "positive_probability", "negative_probability"}

    review 본문에 학습 사전에 있는 단어가 하나도 안 걸리면("ㄹㅇㅎ" 같은 의미 없는 입력이거나
    사전에 없는 새 표현) 모델은 사실상 아무 근거 없이 학습 데이터의 클래스 비율(prior)만으로
    찍는 셈이라 신뢰할 수 없다. 이럴 때 rating이 같이 주어지면 텍스트 대신 별점으로 판단한다:
    3점 이하는 부정, 4~5점은 긍정.
    """
    if not is_ready():
        raise RuntimeError("감성분석 모델이 로드되지 않았습니다. train_sentiment.py를 먼저 실행하세요.")

    X = vectorizer.transform([review])
    # 정규화된 단어 중 학습 어휘(TF-IDF vocabulary)에 하나도 안 걸리면 벡터가 전부 0이 된다.
    is_recognized = X.nnz > 0
    prediction = model.predict(X)[0]
    probabilities = model.predict_proba(X)[0]
    # scikit-learn은 클래스를 알파벳순으로 정렬한다: negative가 0번, positive가 1번.
    classes = list(model.classes_)
    positive_idx = classes.index("positive")
    negative_idx = classes.index("negative")

    # numpy.float64를 그대로 반환하면 JSON 직렬화 문제가 생길 수 있어 float()으로 감싼다.
    positive_probability = float(probabilities[positive_idx])
    negative_probability = float(probabilities[negative_idx])

    if not is_recognized and rating is not None:
        is_positive = rating >= 4
        prediction = "positive" if is_positive else "negative"
        positive_probability = 1.0 if is_positive else 0.0
        negative_probability = 1.0 - positive_probability

    return {
        "review": review,
        "prediction": str(prediction),
        "positive_probability": positive_probability,
        "negative_probability": negative_probability,
    }


def summarize_restaurant(restaurant_id: int, restaurant_name: str, reviews: list[dict]) -> dict:
    """한 매장의 리뷰 여러 개를 분석해서 매장 단위 집계 결과를 만든다.

    reviews: [{"content": str, "rating": int|None}, ...]
    """
    predictions = [predict_one(r["content"], r.get("rating")) for r in reviews]
    positive_count = sum(1 for p in predictions if p["prediction"] == "positive")
    negative_count = len(predictions) - positive_count
    total = len(predictions)
    positive_ratio = round((positive_count / total) * 100, 1) if total > 0 else 0.0

    return {
        "restaurantId": restaurant_id,
        "restaurantName": restaurant_name,
        "reviewCount": total,
        "positiveCount": positive_count,
        "negativeCount": negative_count,
        "positiveRatio": positive_ratio,
    }
