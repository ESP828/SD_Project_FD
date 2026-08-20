# ai/train_sentiment.py
"""
리뷰 감성분석(긍정/부정) 모델 학습 스크립트.

data/reviews_naive_bayes.csv (restaurant_id,restaurant_name,review,label, 실제 DB 리뷰)에
lexicon_augmentation.py의 짧은 키워드/구 단위 보강 데이터를 합쳐서 학습한다. 보강 데이터를
섞는 이유: 실제 리뷰 문장만으로는 "별로에요", "가성비 굿"처럼 사용자가 흔히 쓰는 짧은
표현이 학습 데이터에 없어서, 그런 문장이 들어오면 모델이 근거 없이 클래스 비율(예: 긍정
67%)로만 찍는 문제가 있었다.

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

from lexicon_augmentation import build_augmentation_rows
from text_normalize import normalize_text

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_PATH = os.path.join(BASE_DIR, "data", "reviews_naive_bayes.csv")
MODEL_DIR = os.path.join(BASE_DIR, "model")


def load_real_reviews():
    return pd.read_csv(DATA_PATH)[["review", "label"]]


def load_augmentation():
    rows = build_augmentation_rows()
    return pd.DataFrame(rows, columns=["review", "label"])


def main():
    real_df = load_real_reviews()
    augmentation_df = load_augmentation()
    print(f"실제 리뷰: {len(real_df)}개 + 키워드 보강 데이터: {len(augmentation_df)}개")
    print(f"positive={sum(real_df['label'] == 'positive') + sum(augmentation_df['label'] == 'positive')}, "
          f"negative={sum(real_df['label'] == 'negative') + sum(augmentation_df['label'] == 'negative')}")

    # 실제 DB 리뷰만 train/test로 나눠서 "학습 안 해본 문장을 얼마나 잘 맞추는지"를 평가한다.
    # 사전 보강 단어(lexicon_augmentation)는 평가용 held-out 데이터가 아니라 어휘 주입이
    # 목적이므로 무작위 분할 대상에서 빼고 전부 학습에 넣는다 - 안 그러면 특정 단어가 우연히
    # test 쪽으로만 빠져서 그 단어를 모델이 한 번도 못 보고 넘어가는 문제가 생긴다.
    real_train, real_test, y_real_train, y_real_test = train_test_split(
        real_df["review"], real_df["label"],
        test_size=0.2, random_state=42, stratify=real_df["label"],
    )
    X_train = pd.concat([real_train, augmentation_df["review"]], ignore_index=True)
    y_train = pd.concat([y_real_train, augmentation_df["label"]], ignore_index=True)
    X_test, y_test = real_test, y_real_test
    print(f"Train: {len(X_train)}개 (실제 리뷰 {len(real_train)} + 보강 단어 {len(augmentation_df)}) / "
          f"Test: {len(X_test)}개 (전부 실제 리뷰)")

    # 한글 조사/어미가 많이 붙는 특성상 1~2글자 토큰도 살리고, 두 단어 묶음(bigram)까지 본다.
    # preprocessor로 종결어미 정규화를 적용해서 "별로에요"/"별로였어요" 같은 활용형 차이를 줄인다.
    vectorizer = TfidfVectorizer(
        preprocessor=normalize_text,
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
