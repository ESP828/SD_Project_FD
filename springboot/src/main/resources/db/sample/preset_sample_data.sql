-- 개발 DB에서 Presset 목록/상세 화면을 확인하기 위한 선택 실행용 데이터입니다.
-- V6__create_preset_tables.sql을 먼저 적용한 뒤 DBeaver에서 실행하세요.
-- 운영 DB에는 검토 없이 실행하지 마세요.

START TRANSACTION;

INSERT INTO preset (
    title,
    summary,
    description,
    image_url,
    category,
    display_order,
    status
)
SELECT
    '성수 데이트 맛집',
    '분위기 좋은 성수 맛집을 한 번에 확인해 보세요.',
    '데이트하기 좋은 분위기와 메뉴를 갖춘 성수 음식점을 모은 프리셋입니다.',
    NULL,
    '데이트',
    1,
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM preset WHERE title = '성수 데이트 맛집'
);

INSERT INTO preset (
    title,
    summary,
    description,
    image_url,
    category,
    display_order,
    status
)
SELECT
    '가족 외식 추천 맛집',
    '가족과 함께 편하게 식사할 수 있는 맛집입니다.',
    '여러 연령대가 함께 방문하기 좋은 음식점을 모은 프리셋입니다.',
    NULL,
    '가족 외식',
    2,
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM preset WHERE title = '가족 외식 추천 맛집'
);

INSERT INTO preset (
    title,
    summary,
    description,
    image_url,
    category,
    display_order,
    status
)
SELECT
    '혼자 먹기 좋은 맛집',
    '혼자 방문해도 부담 없는 음식점을 모았습니다.',
    '1인 좌석과 혼밥 메뉴를 이용하기 좋은 음식점을 모은 프리셋입니다.',
    NULL,
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
