-- 공공데이터 출처 음식점(public_restaurant)에도 메뉴를 연결할 수 있도록
-- menu 테이블에 public_restaurant_id를 추가하고, restaurant_id를 nullable로 바꾼다.
-- 메뉴 한 건은 restaurant_id, public_restaurant_id 둘 중 정확히 하나만 채운다(애플리케이션에서 보장).
-- menu는 review/favorite와 달리 surrogate PK(menu_id)를 이미 쓰고 있어 PK 교체가 필요 없다.
ALTER TABLE menu
    MODIFY COLUMN restaurant_id BIGINT UNSIGNED NULL,
    ADD COLUMN public_restaurant_id BIGINT UNSIGNED NULL AFTER restaurant_id,
    ADD CONSTRAINT fk_menu_public_restaurant
        FOREIGN KEY (public_restaurant_id) REFERENCES public_restaurant (public_restaurant_id)
        ON DELETE CASCADE,
    ADD KEY idx_menu_public_restaurant (public_restaurant_id, status);
