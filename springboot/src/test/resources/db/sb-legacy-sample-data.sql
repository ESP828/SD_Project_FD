INSERT INTO role (role_id, role_code, role_name)
VALUES
    (1, 'ROLE_USER', '일반 사용자'),
    (2, 'ROLE_OWNER', '사업자'),
    (3, 'ROLE_ADMIN', '관리자');

INSERT INTO account (
    account_id, username, password, email, nickname, is_email_verified,
    status, is_deleted, role, provider, provider_id
)
VALUES
    (1, 'local_user', '$argon2id$legacy-local-hash', 'local@example.com', '로컬회원', 1,
     'ACTIVE', 0, 'USER', 'LOCAL', NULL),
    (2, 'kakao_12345', '$argon2id$legacy-social-placeholder', 'kakao_12345@kakao.local', '카카오회원', 0,
     'ACTIVE', 0, 'OWNER', 'KAKAO', '12345'),
    (3, 'withdrawn_user', '$argon2id$legacy-withdrawn-hash', 'withdrawn@example.com', '탈퇴회원', 1,
     'INACTIVE', 1, 'USER', 'LOCAL', NULL);

INSERT INTO account_role (account_id, role_id)
VALUES (1, 1), (2, 1), (2, 2), (3, 1);

INSERT INTO restaurant_category (category_id, parent_id, name)
VALUES (1, NULL, '한식');

INSERT INTO restaurant (
    restaurant_id, account_id, category_id, name, address, latitude, longitude, phone, description, status
)
VALUES
    (1, 2, 1, '테스트 식당', '서울특별시 중구 세종대로 110',
     37.5668260, 126.9786567, '02-000-0000', '마이그레이션 검증용', 'OPEN');

INSERT INTO menu (menu_id, restaurant_id, name, price, description)
VALUES (1, 1, '테스트 메뉴', 10000, '대표 메뉴 후보');

INSERT INTO post (
    post_id, account_id, restaurant_id, board_type, category, title, content
)
VALUES (1, 1, 1, 'GENERAL', '후기', '테스트 게시글', '마이그레이션 검증');

INSERT INTO comment (comment_id, post_id, account_id, content)
VALUES (1, 1, 2, '테스트 댓글');

INSERT INTO review (review_id, restaurant_id, account_id, rating, content)
VALUES (1, 1, 1, 5, '테스트 리뷰');

INSERT INTO favorite (account_id, restaurant_id)
VALUES (1, 1);

INSERT INTO notification (notification_id, account_id, type, content, ref_id, is_read)
VALUES (1, 1, 'COMMENT', '댓글 알림', 1, 1);
