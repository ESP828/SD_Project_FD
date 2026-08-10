import os
import json
import hashlib
import pandas as pd
import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer

# 1. 경로 설정
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CSV_PATH = os.path.join(BASE_DIR, 'data', 'public-restaurants.csv')
RULES_PATH = os.path.join(BASE_DIR, '..', 'src', 'main', 'resources', 'recommendation', 'text-rules.json')
OUTPUT_DIR = os.path.join(BASE_DIR, '..', 'src', 'main', 'resources', 'recommendation', 'model')

def load_rules(rules_path):
    with open(rules_path, 'r', encoding='utf-8') as f:
        return json.load(f)

def compute_file_hash(filepath):
    hasher = hashlib.sha256()
    with open(filepath, 'rb') as f:
        hasher.update(f.read())
    return hasher.hexdigest()

def custom_tokenizer(text, rules):
    if not text or not isinstance(text, str):
        return []

    tokens = text.split()
    stopwords = set(rules.get('stopwords', []))
    postpositions = sorted(rules.get('postpositions', []), key=len, reverse=True)
    synonyms = rules.get('synonyms', {})

    result = []
    for token in tokens:
        # 조사 제거 (긴 조사부터)
        stripped = token
        for post in postpositions:
            if stripped.endswith(post) and len(stripped) > len(post):
                stripped = stripped[:-len(post)]
                break

        # 불용어 건너뛰기
        if stripped in stopwords:
            continue

        # 동의어 확장
        if stripped in synonyms:
            result.extend(synonyms[stripped])
        elif len(stripped) >= 2:
            result.append(stripped)

    return result

def main():
    print("=== 푸드덕 TF-IDF 오프라인 학습 시작 ===")

    # 2. 파일 존재 확인
    if not os.path.exists(CSV_PATH):
        print(f"[오류] 학습용 CSV 파일이 없습니다: {CSV_PATH}")
        print("-> Spring Boot에서 공공 음식점 데이터를 CSV로 생성해 주거나, 테스트용 CSV를 data/ 폴더에 넣어주세요.")
        return
    if not os.path.exists(RULES_PATH):
        print(f"[오류] 텍스트 규칙 파일이 없습니다: {RULES_PATH}")
        return

    # 3. 규칙 및 데이터 로드
    rules = load_rules(RULES_PATH)
    rules_hash = compute_file_hash(RULES_PATH)

    df = pd.read_csv(CSV_PATH)
    print(f"- 총 {len(df)}건의 음식점 데이터 로드 완료")

    # 4. 학습용 문서(Document) 생성
    documents = []
    for _, row in df.iterrows():
        doc_parts = [
            str(row.get('name', '')),
            str(row.get('category_large_name', '')),
            str(row.get('category_medium_name', '')),
            str(row.get('category_small_name', '')),
            str(row.get('road_address', ''))
        ]
        documents.append(" ".join(doc_parts))

    # 5. TF-IDF 학습
    vectorizer = TfidfVectorizer(
        tokenizer=lambda x: custom_tokenizer(text=x, rules=rules),
        ngram_range=(1, 2),
        min_df=1, # 데이터량이 적은 초기 테스트 단계에서는 1로 설정
        smooth_idf=True,
        sublinear_tf=False,
        norm='l2'
    )

    print("- TF-IDF 학습 실행 중...")
    vectorizer.fit(documents)

    vocab = vectorizer.vocabulary_
    idf = vectorizer.idf_.tolist()

    sorted_vocab = {k: int(v) for k, v in sorted(vocab.items(), key=lambda item: item[1])}

    # 6. 결과 저장 디렉토리 생성 및 파일 저장
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    vocab_path = os.path.join(OUTPUT_DIR, 'vocabulary.json')
    idf_path = os.path.join(OUTPUT_DIR, 'idf.json')
    meta_path = os.path.join(OUTPUT_DIR, 'model-meta.json')

    with open(vocab_path, 'w', encoding='utf-8') as f:
        json.dump(sorted_vocab, f, ensure_ascii=False, indent=2)

    with open(idf_path, 'w', encoding='utf-8') as f:
        json.dump(idf, f, ensure_ascii=False, indent=2)

    meta_data = {
        "modelVersion": "fooduck-tfidf-v1",
        "trainedAt": pd.Timestamp.now().isoformat(),
        "documentCount": len(documents),
        "vocabularySize": len(sorted_vocab),
        "ngramMin": 1,
        "ngramMax": 2,
        "smoothIdf": True,
        "sublinearTf": False,
        "normalization": "L2",
        "rulesVersion": rules.get("version", "fooduck-text-rules-v1"),
        "rulesSha256": rules_hash
    }

    with open(meta_path, 'w', encoding='utf-8') as f:
        json.dump(meta_data, f, ensure_ascii=False, indent=2)

    print(f"=== 학습 완료! ===")
    print(f"- 생성 파일 위치: {os.path.abspath(OUTPUT_DIR)}")
    print(f"- 단어 사전 크기: {len(sorted_vocab)}개 토큰")

if __name__ == '__main__':
    main()
