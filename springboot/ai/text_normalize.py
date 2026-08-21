# ai/text_normalize.py
"""
한국어 리뷰 텍스트 정규화.

핵심 방식: "별로"라는 단어가 뒤에 뭐가 붙든("별로야", "별로예요", "별로였음", "완전별로")
그 어절 안에 알려진 감성 단어(lexicon_augmentation.py의 긍정/부정 단어)가 포함돼 있으면,
조사/어미 종류와 상관없이 그 단어로 인식하게 만든다. 어미 목록을 일일이 나열해서 제거하는
방식은 반말("별로야")처럼 목록에 없는 활용형이 나오면 그때마다 빠뜨리는 문제가 있었다.

TfidfVectorizer의 preprocessor로 등록해서 학습/추론 시 항상 동일하게 적용한다.
"""
from lexicon_augmentation import POSITIVE_WORDS, NEGATIVE_WORDS

# 긴 단어부터 먼저 검사해야 짧은 단어가 잘못 걸리지 않는다.
# 예: "가성비꽝"을 검사할 때 "가성비"보다 "가성비꽝"을 먼저 봐야 정확하다.
_KNOWN_STEMS = sorted(set(POSITIVE_WORDS) | set(NEGATIVE_WORDS), key=len, reverse=True)

# 그래도 못 잡는 표현을 위한 보조 어미 제거(사전에 없는 새 단어에 한해서만 동작).
_SUFFIXES = sorted([
    "습니다", "입니다", "였습니다", "했습니다",
    "이었어요", "였어요", "했어요", "이에요", "예요", "에요",
    "해요", "네요", "어요", "아요", "았어요", "었어요",
    "이었어", "이야", "았어", "었어", "야",
], key=len, reverse=True)


def _normalize_word(word: str) -> str:
    # 1) 어절 안에 알려진 감성 단어가 포함돼 있으면, 조사/어미가 뭐든 그 단어로 정규화한다.
    for stem in _KNOWN_STEMS:
        if stem in word:
            return stem
    # 2) 사전에 없는 새로운 단어는, 흔한 종결어미만 최대한 제거해서 어간을 살린다.
    for suffix in _SUFFIXES:
        if word.endswith(suffix) and len(word) - len(suffix) >= 2:
            return word[: -len(suffix)]
    return word


def normalize_text(text: str) -> str:
    if not text:
        return text
    return " ".join(_normalize_word(w) for w in text.split())
