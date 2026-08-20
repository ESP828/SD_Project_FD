# ai/text_normalize.py
"""
한국어 리뷰 텍스트의 아주 가벼운 어미 정규화.

형태소 분석기(Okt 등) 없이 순수 TF-IDF만 쓰다 보니, "별로에요"/"별로였어요"/"별로네요"처럼
의미는 같지만 활용형이 다른 단어가 서로 완전히 다른 토큰으로 취급되어 학습 데이터에 없는
활용형이 나오면 모델이 아무 근거 없이 클래스 비율(예: 긍정 67%)로만 찍는 문제가 있었다.

이 모듈은 흔한 종결어미를 어절 끝에서 제거해서 여러 활용형이 최대한 같은 어간으로 모이게
한다. 완벽한 형태소 분석은 아니지만(예: 불규칙 활용은 처리 못함), 어휘 공백 문제를 크게
줄여준다. TfidfVectorizer의 preprocessor로 등록해서 학습/추론 시 항상 동일하게 적용한다.
"""

# 어절(word) 끝에서부터 매칭한다 - 긴 접미사를 먼저 시도해야 짧은 접미사가 잘못 걸리지 않는다.
_SUFFIXES = sorted([
    "습니다", "입니다", "였습니다", "했습니다",
    "이었어요", "였어요", "했어요", "이에요", "예요", "에요",
    "해요", "네요", "어요", "아요",
], key=len, reverse=True)


def _normalize_word(word: str) -> str:
    for suffix in _SUFFIXES:
        # 접미사를 떼고도 어간이 최소 2글자는 남아야 한다 (너무 짧아지면 의미가 사라짐).
        if word.endswith(suffix) and len(word) - len(suffix) >= 2:
            return word[: -len(suffix)]
    return word


def normalize_text(text: str) -> str:
    if not text:
        return text
    return " ".join(_normalize_word(w) for w in text.split())
