# ai/sentiment.py
"""
train_sentiment.py로 학습해 둔 Naive Bayes 감성분석 모델을 로드하고,
리뷰 텍스트(들)를 긍정/부정으로 분류하는 함수를 제공한다.

recommend.py와 동일한 패턴으로, 모델은 모듈 임포트 시(=서버 기동 시) 1회만 로드한다.
"""
import os
import joblib

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


def predict_one(review: str) -> dict:
    """단일 리뷰 -> {"review", "prediction", "positive_probability", "negative_probability"}"""
    if not is_ready():
        raise RuntimeError("감성분석 모델이 로드되지 않았습니다. train_sentiment.py를 먼저 실행하세요.")

    X = vectorizer.transform([review])
    prediction = model.predict(X)[0]
    probabilities = model.predict_proba(X)[0]
    # scikit-learn은 클래스를 알파벳순으로 정렬한다: negative가 0번, positive가 1번.
    classes = list(model.classes_)
    positive_idx = classes.index("positive")
    negative_idx = classes.index("negative")

    return {
        "review": review,
        "prediction": str(prediction),
        # numpy.float64를 그대로 반환하면 JSON 직렬화 문제가 생길 수 있어 float()으로 감싼다.
        "positive_probability": float(probabilities[positive_idx]),
        "negative_probability": float(probabilities[negative_idx]),
    }


def summarize_restaurant(restaurant_id: int, restaurant_name: str, reviews: list[str]) -> dict:
    """한 매장의 리뷰 여러 개를 분석해서 매장 단위 집계 결과를 만든다."""
    predictions = [predict_one(r) for r in reviews]
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
