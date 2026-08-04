import re

STOPWORDS = { "맛집", "추천", "추천해줘", "추천해주세요", "해주세요", "해줘", "찾아줘",
    "좋은", "있는", "근처", "음식점", }

POSTPOSITIONS = [ "에서", "으로", "에게", "까지", "부터", "에는", "에서는", "이라면", "이라", "에서도", "에서만",
    "하고", "이랑", "랑", "과", "와", "은", "는", "이", "가", "을", "를", "에",]

SYNONYM = {
    "파스타집": "파스타", "이태리": "이탈리안", "초밥집": "초밥",
    "중국집": "중식", "혼자": "혼밥", "데이트코스": "데이트",
}


def normalize(word: str):

    if word in SYNONYM:
        word = SYNONYM[word]

    for p in POSTPOSITIONS:
        if word.endswith(p):
            word = word[:-len(p)]
    return word


def extract_keywords(sentence):

    words = re.findall(r"[가-힣A-Za-z0-9]+", sentence)
    result = []

    for word in words:
        word = normalize(word)

        if len(word) < 2:
            continue

        if word in STOPWORDS:
            continue

        if word not in result:
            result.append(word)
    return result


if __name__ == "__main__":
    while True:

        query = input("검색어 : ")
        print(extract_keywords(query))
