from konlpy.tag import Okt

okt = Okt()

STOPWORDS = {
    "맛집",
    "추천",
    "추천하다",
    "찾다",
    "있다",
    "좋다",
    "가다",
    "먹다",
    "음식점",
    "근처",
}

SYNONYM = {
    "파스타집": "파스타",
    "초밥집": "초밥",
    "중국집": "중식",
    "이태리": "이탈리안",
    "혼자": "혼밥",
    "데이트코스": "데이트",
}

REGIONS = {
    "강남",
    "홍대",
    "잠실",
    "건대",
    "성수"
}

CATEGORIES = {
    "파스타",
    "초밥",
    "중식",
    "양식",
    "한식",
    "돈까스"
}

TAGS = {
    "데이트",
    "혼밥",
    "가족",
    "모임"
}


def normalize(word):
    return SYNONYM.get(word, word)


def parse_keywords(words):

    result = {
        "region": None,
        "category": None,
        "keyword": None
    }

    for word in words:

        if word in REGIONS:
            result["region"] = word

        elif word in CATEGORIES:
            result["category"] = word

        elif word in TAGS:
            result["keyword"] = word

    return result

def extract_keywords(sentence):

    keywords = []

    morphs = okt.pos(sentence, stem=True)

    for word, pos in morphs:

        # 명사만 사용
        if pos != "Noun":
            continue

        word = normalize(word)

        if len(word) < 2:
            continue

        if word in STOPWORDS:
            continue

        if word not in keywords:
            keywords.append(word)

    return keywords
