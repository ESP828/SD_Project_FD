-- 공공데이터 43개 세분류(category_small_name)를 8개 대분류로 묶은 category_group 컬럼을 추가한다.
-- 원본 정부 분류(category_large/medium/small_name, 코드)는 그대로 두고 파생 컬럼만 추가한다.
-- 값: 패스트푸드 / 한식 / 중식 / 양식 / 카페·디저트 / 일식 / 주점 / 아시안
ALTER TABLE public_restaurant
    ADD COLUMN category_group VARCHAR(20) NULL,
    ADD KEY idx_public_restaurant_category_group (category_group);
