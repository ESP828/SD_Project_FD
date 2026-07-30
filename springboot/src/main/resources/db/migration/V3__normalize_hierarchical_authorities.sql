-- 권한 ID를 계층 레벨과 동일하게 고정한다.
-- 0: 일반 사용자, 1: 사업자, 2: 관리자

DROP TRIGGER IF EXISTS trg_account_default_authority;

DROP TEMPORARY TABLE IF EXISTS authority_id_remap;
CREATE TEMPORARY TABLE authority_id_remap (
    old_authority_id SMALLINT UNSIGNED NOT NULL,
    new_authority_id SMALLINT UNSIGNED NOT NULL,
    PRIMARY KEY (old_authority_id),
    CONSTRAINT uk_authority_id_remap_new UNIQUE (new_authority_id)
);

INSERT INTO authority_id_remap (old_authority_id, new_authority_id)
SELECT
    authority_id,
    CASE authority_code
        WHEN 'ROLE_USER' THEN 0
        WHEN 'ROLE_BUSINESS' THEN 1
        WHEN 'ROLE_ADMIN' THEN 2
    END
FROM authority
WHERE authority_code IN ('ROLE_USER', 'ROLE_BUSINESS', 'ROLE_ADMIN');

ALTER TABLE account_authority
    DROP FOREIGN KEY fk_account_authority_authority;

ALTER TABLE authority
    MODIFY authority_id SMALLINT UNSIGNED NOT NULL;

-- PK 충돌 없이 기존 ID를 0/1/2로 옮기기 위한 임시 구간이다.
UPDATE account_authority account_role
JOIN authority_id_remap remap
    ON remap.old_authority_id = account_role.authority_id
SET account_role.authority_id = remap.new_authority_id + 100;

UPDATE authority authority_role
JOIN authority_id_remap remap
    ON remap.old_authority_id = authority_role.authority_id
SET authority_role.authority_id = remap.new_authority_id + 100;

UPDATE authority
SET authority_id = authority_id - 100,
authority_name = CASE authority_code
    WHEN 'ROLE_USER' THEN '일반 사용자'
    WHEN 'ROLE_BUSINESS' THEN '사업자'
    WHEN 'ROLE_ADMIN' THEN '관리자'
END
WHERE authority_code IN ('ROLE_USER', 'ROLE_BUSINESS', 'ROLE_ADMIN');

UPDATE account_authority
SET authority_id = authority_id - 100
WHERE authority_id IN (100, 101, 102);

DROP TEMPORARY TABLE authority_id_remap;

INSERT INTO authority (authority_id, authority_code, authority_name)
SELECT 0, 'ROLE_USER', '일반 사용자'
WHERE NOT EXISTS (
    SELECT 1 FROM authority WHERE authority_code = 'ROLE_USER'
);

INSERT INTO authority (authority_id, authority_code, authority_name)
SELECT 1, 'ROLE_BUSINESS', '사업자'
WHERE NOT EXISTS (
    SELECT 1 FROM authority WHERE authority_code = 'ROLE_BUSINESS'
);

INSERT INTO authority (authority_id, authority_code, authority_name)
SELECT 2, 'ROLE_ADMIN', '관리자'
WHERE NOT EXISTS (
    SELECT 1 FROM authority WHERE authority_code = 'ROLE_ADMIN'
);

ALTER TABLE account_authority
    MODIFY authority_id SMALLINT UNSIGNED NOT NULL DEFAULT 0;

-- 권한이 없던 계정과 상위 권한 계정에 누적 권한을 채운다.
INSERT INTO account_authority (account_id, authority_id, created_at)
SELECT account.account_id, 0, CURRENT_TIMESTAMP(6)
FROM account
LEFT JOIN account_authority user_authority
    ON user_authority.account_id = account.account_id
    AND user_authority.authority_id = 0
WHERE user_authority.account_id IS NULL;

INSERT INTO account_authority (account_id, authority_id, created_at)
SELECT admin_authority.account_id, 1, CURRENT_TIMESTAMP(6)
FROM account_authority admin_authority
LEFT JOIN account_authority business_authority
    ON business_authority.account_id = admin_authority.account_id
    AND business_authority.authority_id = 1
WHERE admin_authority.authority_id = 2
  AND business_authority.account_id IS NULL;

ALTER TABLE account_authority
    ADD CONSTRAINT fk_account_authority_authority
        FOREIGN KEY (authority_id) REFERENCES authority (authority_id) ON DELETE RESTRICT;

-- 애플리케이션 밖에서 계정을 추가해도 일반 사용자 권한을 기본 부여한다.
CREATE TRIGGER trg_account_default_authority
AFTER INSERT ON account
FOR EACH ROW
INSERT IGNORE INTO account_authority (account_id, authority_id, created_at)
VALUES (NEW.account_id, 0, CURRENT_TIMESTAMP(6));
