from konlpy.tag import Okt

okt = Okt()

STOPWORDS = {
    "맛집", "추천", "추천하다", "찾다", "있다", "좋다", "가다", "먹다",
    "음식점", "식당", "근처", "주변", "곳", "알다", "알려주다", "어디"
}

SYNONYM = {
    "파스타집": "파스타",
    "초밥집": "초밥",
    "중국집": "중식",
    "이태리": "이탈리안",
    "혼자": "혼밥",
    "데이트코스": "데이트",
    "고깃집": "고기",
    "술집": "주점"
}

# 기본 식별용 사전 (확장 가능)
REGIONS = {"강남", "홍대", "잠실", "건대", "성수", "신촌", "명동", "종로", "여의도", "이태원", "마포", "서초", "역삼"}
CATEGORIES = {"파스타", "초밥", "중식", "양식", "한식", "돈까스", "일식", "카페", "디저트", "치킨", "피자", "고기", "국밥"}
TAGS = {"데이트", "혼밥", "가족", "모임", "회식", "분위기", "조용한", "깔끔한", "가성비"}

def normalize(word):
    return SYNONYM.get(word, word)

def extract_and_parse(sentence: str):
    """
    자연어 문장을 분석하여 구조화된 검색 조건(지역, 카테고리, 일반 키워드 목록)을 반환합니다.
    """
    if not sentence or not sentence.strip():
        return {"region": None, "category": None, "keywords": []}

    tokens = okt.pos(sentence, stem=True)

    region = None
    category = None
    extracted_keywords = []

    for word, pos in tokens:
        word = normalize(word)
        if word in STOPWORDS or len(word) < 2:
            continue

        # 1. 지역 추출
        if word in REGIONS and not region:
            region = word
        # 2. 대표 카테고리 추출
        elif word in CATEGORIES and not category:
            category = word
        # 3. 그 외 명사/형용사/태그는 검색 키워드로 수집
        elif pos in ["Noun", "Adjective"] or word in TAGS:
            extracted_keywords.append(word)

    return {
        "region": region,
        "category": category,
        "keywords": list(set(extracted_keywords))  # 중복 제거
    }
