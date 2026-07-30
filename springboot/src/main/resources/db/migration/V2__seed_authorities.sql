INSERT INTO authority (authority_code, authority_name)
VALUES
    ('ROLE_USER', '일반 사용자'),
    ('ROLE_BUSINESS', '사업자'),
    ('ROLE_ADMIN', '관리자')
ON DUPLICATE KEY UPDATE
    authority_name = VALUES(authority_name);
