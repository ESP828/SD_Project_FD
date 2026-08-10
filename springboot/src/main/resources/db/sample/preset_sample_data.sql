-- 개발 DB에서 Presset 목록/상세 화면을 확인하기 위한 선택 실행용 데이터입니다.
-- V6__create_preset_tables.sql을 먼저 적용한 뒤 DBeaver에서 실행하세요.
-- 운영 DB에는 검토 없이 실행하지 마세요.

START TRANSACTION;

INSERT INTO preset (
    title,
    category,
    display_order,
    status
)
SELECT
    '성수 데이트 맛집',
    '데이트',
    1,
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM preset WHERE title = '성수 데이트 맛집'
);

INSERT INTO preset (
    title,
    category,
    display_order,
    status
)
SELECT
    '가족 외식 추천 맛집',
    '가족 외식',
    2,
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM preset WHERE title = '가족 외식 추천 맛집'
);

INSERT INTO preset (
    title,
    category,
    display_order,
    status
)
SELECT
    '혼자 먹기 좋은 맛집',
    '혼밥',
    3,
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM preset WHERE title = '혼자 먹기 좋은 맛집'
);

COMMIT;

SELECT
    preset_id,
    title,
    category,
    display_order,
    status
FROM preset
ORDER BY display_order, preset_id;

-- 실제 restaurant_id를 확인한 뒤 아래 예시의 숫자를 바꾸어 별도로 실행하세요.
-- SELECT restaurant_id, name, status
-- FROM restaurant
-- WHERE status = 'ACTIVE'
-- ORDER BY restaurant_id
-- LIMIT 20;
--
-- INSERT INTO preset_restaurant (
--     preset_id, restaurant_id, display_order, description
-- ) VALUES
--     (1, 1, 1, '데이트 분위기가 좋은 곳'),
--     (1, 2, 2, '함께 나누어 먹기 좋은 메뉴가 있는 곳')
-- ON DUPLICATE KEY UPDATE
--     display_order = VALUES(display_order),
--     description = VALUES(description);
