-- 공공데이터 출처 음식점(public_restaurant)에도 우리 사이트 유저가 리뷰를 남길 수 있도록
-- review 테이블에 public_restaurant_id를 추가하고, restaurant_id를 nullable로 바꾼다.
-- 리뷰 한 건은 restaurant_id, public_restaurant_id 둘 중 정확히 하나만 채운다(애플리케이션에서 보장).
ALTER TABLE review
    MODIFY COLUMN restaurant_id BIGINT UNSIGNED NULL,
    ADD COLUMN public_restaurant_id BIGINT UNSIGNED NULL AFTER restaurant_id,
    ADD CONSTRAINT fk_review_public_restaurant
        FOREIGN KEY (public_restaurant_id) REFERENCES public_restaurant (public_restaurant_id)
        ON DELETE CASCADE,
    ADD KEY idx_review_public_restaurant (public_restaurant_id, status);
