INSERT INTO restaurant_category (
    parent_id,
    category_code,
    name,
    display_order,
    active
) VALUES
    (NULL, 'KOREAN', '한식', 10, 1),
    (NULL, 'CHINESE', '중식', 20, 1),
    (NULL, 'JAPANESE', '일식', 30, 1),
    (NULL, 'WESTERN', '양식', 40, 1),
    (NULL, 'ASIAN', '아시안', 50, 1),
    (NULL, 'FAST_FOOD', '패스트푸드', 60, 1),
    (NULL, 'CAFE_DESSERT', '카페·디저트', 70, 1),
    (NULL, 'PUB', '주점', 80, 1)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    display_order = VALUES(display_order),
    active = VALUES(active);
