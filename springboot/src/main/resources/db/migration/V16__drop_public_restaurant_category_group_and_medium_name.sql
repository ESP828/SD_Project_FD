-- 사이트 전체 카테고리 표기/필터링 기준을 category_small_name 하나로 통일한다.
-- category_group(자체 8분류)과 category_medium_name(정부 중분류)은 더 이상 쓰지 않으므로 제거한다.
-- category_medium_name은 FULLTEXT 인덱스(ft_public_restaurant_search)에도 포함되어 있어
-- 인덱스를 먼저 재정의한 뒤 컬럼을 삭제한다.
ALTER TABLE public_restaurant
    DROP INDEX ft_public_restaurant_search,
    DROP COLUMN category_group,
    DROP COLUMN category_medium_name,
    ADD FULLTEXT KEY ft_public_restaurant_search (name, category_large_name, category_small_name, road_address, lot_address) WITH PARSER ngram;
