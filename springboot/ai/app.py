from keyword_extractor import extract_keywords, parse_keywords
from search import search_restaurant

while True:

    query = input("검색어 : ")

    words = extract_keywords(query)

    info = parse_keywords(words)

    restaurants = search_restaurant(info)

    if not restaurants:
        print("검색 결과가 없습니다.")
        continue

    print("\n검색 결과")

    for r in restaurants:
        print(f"{r[1]} ({r[2]}) {r[3]} ★{r[5]}")
