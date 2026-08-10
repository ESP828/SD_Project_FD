-- 공공데이터 출처 음식점(public_restaurant)에도 찜하기를 남길 수 있도록
-- favorite 테이블에 public_restaurant_id를 추가하고, restaurant_id를 nullable로 바꾼다.
-- 기존 복합 PK(account_id, restaurant_id)는 restaurant_id NULL을 허용할 수 없으므로
-- 대리키(favorite_id)로 교체하고, 대신 두 개의 부분 유니크 제약으로 중복 찜을 막는다.
-- 찜 한 건은 restaurant_id, public_restaurant_id 둘 중 정확히 하나만 채운다(애플리케이션에서 보장).
-- fk_favorite_account가 기존 PK(선두 컬럼 account_id)에 의존하므로, PK 교체와 대체 인덱스 추가를
-- 하나의 ALTER TABLE 문으로 묶어 중간 상태 검증 없이 최종 스키마만 검증되게 한다.
ALTER TABLE favorite
    DROP PRIMARY KEY,
    ADD COLUMN favorite_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY FIRST,
    MODIFY COLUMN restaurant_id BIGINT UNSIGNED NULL,
    ADD COLUMN public_restaurant_id BIGINT UNSIGNED NULL AFTER restaurant_id,
    ADD CONSTRAINT fk_favorite_public_restaurant
        FOREIGN KEY (public_restaurant_id) REFERENCES public_restaurant (public_restaurant_id)
        ON DELETE CASCADE,
    ADD UNIQUE KEY uq_favorite_restaurant (account_id, restaurant_id),
    ADD UNIQUE KEY uq_favorite_public_restaurant (account_id, public_restaurant_id),
    ADD KEY idx_favorite_public_restaurant (public_restaurant_id);
