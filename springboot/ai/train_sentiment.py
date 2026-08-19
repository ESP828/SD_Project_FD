# ai/train_sentiment.py
"""
리뷰 감성분석(긍정/부정) 모델 학습 스크립트.

data/reviews_naive_bayes.csv (restaurant_id,restaurant_name,review,label)를 읽어서
TfidfVectorizer + MultinomialNB를 학습시키고, model/naive_bayes_model.pkl,
model/tfidf_vectorizer.pkl로 저장한다.

주의: restaurant_id/restaurant_name은 학습 피처로 쓰지 않는다(나중에 결과를 매장별로
집계할 때만 필요한 메타데이터). 모델은 review -> label 로만 학습한다.

실행: python train_sentiment.py
"""
import os
import joblib
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.naive_bayes import MultinomialNB
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_PATH = os.path.join(BASE_DIR, "data", "reviews_naive_bayes.csv")
MODEL_DIR = os.path.join(BASE_DIR, "model")


def main():
    df = pd.read_csv(DATA_PATH)
    print(f"전체 리뷰 수: {len(df)}개 (positive={sum(df['label'] == 'positive')}, negative={sum(df['label'] == 'negative')})")

    X_train, X_test, y_train, y_test = train_test_split(
        df["review"], df["label"],
        test_size=0.2, random_state=42, stratify=df["label"],
    )
    print(f"Train: {len(X_train)}개 / Test: {len(X_test)}개")

    # 한글 조사/어미가 많이 붙는 특성상 1~2글자 토큰도 살리고, 두 단어 묶음(bigram)까지 본다.
    vectorizer = TfidfVectorizer(
        token_pattern=r"(?u)\b\w+\b",
        ngram_range=(1, 2),
        min_df=1,
    )
    X_train_vec = vectorizer.fit_transform(X_train)
    X_test_vec = vectorizer.transform(X_test)

    model = MultinomialNB()
    model.fit(X_train_vec, y_train)

    y_pred = model.predict(X_test_vec)
    accuracy = accuracy_score(y_test, y_pred)

    print(f"\n=== 테스트 정확도: {accuracy:.4f} ===")
    print("\n classification report")
    print(classification_report(y_test, y_pred))
    print("confusion matrix (행=실제, 열=예측, [negative, positive] 순)")
    print(confusion_matrix(y_test, y_pred, labels=["negative", "positive"]))

    os.makedirs(MODEL_DIR, exist_ok=True)
    joblib.dump(model, os.path.join(MODEL_DIR, "naive_bayes_model.pkl"))
    joblib.dump(vectorizer, os.path.join(MODEL_DIR, "tfidf_vectorizer.pkl"))
    print(f"\n모델 저장 완료: {MODEL_DIR}")


if __name__ == "__main__":
    main()
