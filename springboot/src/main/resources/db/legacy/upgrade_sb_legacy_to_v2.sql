SET NAMES utf8mb4;
SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

-- 권한 명칭을 서비스 용어로 통일한다.
RENAME TABLE `role` TO authority, account_role TO account_authority;

ALTER TABLE account_authority
    DROP FOREIGN KEY fk_account_role_account,
    DROP FOREIGN KEY fk_account_role_role;

ALTER TABLE account_authority
    DROP INDEX fk_account_role_role;

ALTER TABLE authority
    CHANGE COLUMN role_id authority_id SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    CHANGE COLUMN role_code authority_code VARCHAR(30) NOT NULL,
    CHANGE COLUMN role_name authority_name VARCHAR(50) NOT NULL,
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    RENAME INDEX uk_role_code TO uk_authority_code;

ALTER TABLE account_authority
    CHANGE COLUMN role_id authority_id SMALLINT UNSIGNED NOT NULL,
    CHANGE COLUMN granted_at created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD KEY idx_account_authority_authority (authority_id),
    ADD CONSTRAINT fk_account_authority_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_account_authority_authority
        FOREIGN KEY (authority_id) REFERENCES authority (authority_id) ON DELETE RESTRICT;

INSERT INTO authority (authority_code, authority_name)
SELECT 'ROLE_USER', '일반 사용자'
WHERE NOT EXISTS (
    SELECT 1 FROM authority WHERE authority_code = 'ROLE_USER'
);

INSERT INTO authority (authority_code, authority_name)
SELECT 'ROLE_BUSINESS', '사업자'
WHERE NOT EXISTS (
    SELECT 1 FROM authority WHERE authority_code IN ('ROLE_BUSINESS', 'ROLE_OWNER')
);

UPDATE authority
SET authority_code = 'ROLE_BUSINESS', authority_name = '사업자'
WHERE authority_code = 'ROLE_OWNER';

INSERT INTO authority (authority_code, authority_name)
SELECT 'ROLE_ADMIN', '관리자'
WHERE NOT EXISTS (
    SELECT 1 FROM authority WHERE authority_code = 'ROLE_ADMIN'
);

INSERT IGNORE INTO account_authority (account_id, authority_id, created_at)
SELECT
    a.account_id,
    au.authority_id,
    CURRENT_TIMESTAMP(6)
FROM account a
JOIN authority au
  ON au.authority_code = CASE UPPER(a.role)
      WHEN 'USER' THEN 'ROLE_USER'
      WHEN 'ROLE_USER' THEN 'ROLE_USER'
      WHEN 'OWNER' THEN 'ROLE_BUSINESS'
      WHEN 'BUSINESS' THEN 'ROLE_BUSINESS'
      WHEN 'ROLE_OWNER' THEN 'ROLE_BUSINESS'
      WHEN 'ROLE_BUSINESS' THEN 'ROLE_BUSINESS'
      WHEN 'ADMIN' THEN 'ROLE_ADMIN'
      WHEN 'ROLE_ADMIN' THEN 'ROLE_ADMIN'
      ELSE 'ROLE_USER'
  END;

-- 일반 로그인 비밀번호와 소셜 식별자를 계정 기본 정보에서 분리한다.
CREATE TABLE account_credential (
    account_id BIGINT UNSIGNED NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    failed_login_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    locked_until DATETIME(6) NULL,
    password_changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (account_id),
    CONSTRAINT fk_account_credential_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='일반 로그인 자격 정보';

INSERT INTO account_credential (account_id, password_hash, created_at, updated_at)
SELECT account_id, password, created_at, updated_at
FROM account
WHERE UPPER(provider) = 'LOCAL';

CREATE TABLE social_account (
    social_account_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    provider ENUM('KAKAO','NAVER','GOOGLE') NOT NULL,
    provider_user_id VARCHAR(191) NOT NULL,
    provider_email VARCHAR(254) NULL,
    provider_nickname VARCHAR(100) NULL,
    connected_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (social_account_id),
    CONSTRAINT uk_social_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT uk_social_account_provider UNIQUE (account_id, provider),
    KEY idx_social_account_account (account_id),
    CONSTRAINT fk_social_account_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='소셜 로그인 연결';

INSERT INTO social_account (
    account_id, provider, provider_user_id, provider_email, provider_nickname, connected_at, updated_at
)
SELECT
    account_id,
    UPPER(provider),
    provider_id,
    CASE
        WHEN email LIKE '%@kakao.local'
          OR email LIKE '%@naver.local'
          OR email LIKE '%@google.local'
        THEN NULL
        ELSE email
    END,
    nickname,
    created_at,
    updated_at
FROM account
WHERE UPPER(provider) IN ('KAKAO','NAVER','GOOGLE')
  AND provider_id IS NOT NULL;

ALTER TABLE account
    CHANGE COLUMN username login_id VARCHAR(50) NULL COMMENT '일반 로그인 아이디',
    MODIFY COLUMN email VARCHAR(254) NULL,
    CHANGE COLUMN is_email_verified email_verified TINYINT(1) NOT NULL DEFAULT 0,
    MODIFY COLUMN status ENUM('ACTIVE','INACTIVE','SUSPENDED','WITHDRAWN') NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN gender ENUM('UNSPECIFIED','MALE','FEMALE','OTHER') NOT NULL DEFAULT 'UNSPECIFIED' AFTER nickname,
    ADD COLUMN birth_date DATE NULL AFTER gender,
    ADD COLUMN profile_image_url VARCHAR(500) NULL AFTER birth_date,
    ADD COLUMN profile_completed TINYINT(1) NOT NULL DEFAULT 0 AFTER email_verified,
    ADD COLUMN last_login_at DATETIME(6) NULL AFTER status,
    ADD COLUMN deleted_at DATETIME(6) NULL AFTER updated_at,
    RENAME INDEX uk_account_username TO uk_account_login_id,
    ADD KEY idx_account_status (status);

UPDATE account
SET status = 'WITHDRAWN',
    deleted_at = COALESCE(deleted_at, updated_at)
WHERE is_deleted = 1;

UPDATE account
SET profile_completed = CASE
        WHEN nickname IS NOT NULL
         AND (email IS NOT NULL OR UPPER(provider) <> 'LOCAL')
        THEN 1
        ELSE 0
    END;

UPDATE account
SET email = NULL
WHERE UPPER(provider) <> 'LOCAL'
  AND (
      email LIKE '%@kakao.local'
      OR email LIKE '%@naver.local'
      OR email LIKE '%@google.local'
  );

ALTER TABLE account
    DROP COLUMN password,
    DROP COLUMN role,
    DROP COLUMN provider,
    DROP COLUMN provider_id,
    DROP COLUMN is_deleted;

-- 인증번호는 원문 대신 해시만 저장하고 가입 전 이메일도 지원한다.
ALTER TABLE email_verification
    DROP FOREIGN KEY fk_email_verification_account,
    ADD COLUMN email VARCHAR(254) NULL AFTER account_id,
    ADD COLUMN purpose ENUM('SIGN_UP','PASSWORD_RESET','EMAIL_CHANGE') NOT NULL DEFAULT 'SIGN_UP' AFTER email,
    ADD COLUMN attempt_count SMALLINT UNSIGNED NOT NULL DEFAULT 0 AFTER verified_at;

UPDATE email_verification ev
JOIN account a ON a.account_id = ev.account_id
SET ev.email = a.email;

UPDATE email_verification
SET token = SHA2(token, 256);

ALTER TABLE email_verification
    MODIFY COLUMN account_id BIGINT UNSIGNED NULL,
    MODIFY COLUMN email VARCHAR(254) NOT NULL,
    CHANGE COLUMN token code_hash CHAR(64) NOT NULL,
    ADD KEY idx_email_verification_lookup (email, purpose, expires_at),
    ADD CONSTRAINT fk_email_verification_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE CASCADE;

-- 기존 리프레시 토큰 원문을 해시로 바꾼 새 테이블에 이관한다.
CREATE TABLE refresh_token_v2 (
    refresh_token_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (refresh_token_id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    KEY idx_refresh_token_account_expiry (account_id, expires_at),
    CONSTRAINT fk_refresh_token_v2_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='리프레시 토큰 해시';

INSERT IGNORE INTO refresh_token_v2 (
    refresh_token_id, account_id, token_hash, expires_at, created_at
)
SELECT refresh_token_id, account_id, SHA2(token, 256), expires_at, created_at
FROM refresh_token;

DROP TABLE refresh_token;
RENAME TABLE refresh_token_v2 TO refresh_token;

-- 사업자 신청과 프로필의 상태·이력 필드를 보완한다.
ALTER TABLE business_application
    MODIFY COLUMN status ENUM('PENDING','APPROVED','REJECTED','CANCELED') NOT NULL DEFAULT 'PENDING',
    ADD COLUMN canceled_at DATETIME(6) NULL AFTER processed_at,
    ADD COLUMN pending_account_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (
            CASE WHEN status = 'PENDING' THEN account_id ELSE NULL END
        ) STORED AFTER canceled_at,
    ADD COLUMN pending_business_number VARCHAR(20)
        GENERATED ALWAYS AS (
            CASE WHEN status = 'PENDING' THEN business_number ELSE NULL END
        ) STORED AFTER pending_account_id,
    ADD CONSTRAINT uk_business_application_pending_account UNIQUE (pending_account_id),
    ADD CONSTRAINT uk_business_application_pending_number UNIQUE (pending_business_number);

ALTER TABLE business_profile
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) AFTER created_at,
    ADD CONSTRAINT uk_business_profile_number UNIQUE (business_number);

-- 음식점 검색·지도·메뉴 표시용 필드를 보완한다.
ALTER TABLE restaurant_category
    ADD COLUMN category_code VARCHAR(50) NULL AFTER parent_id,
    ADD COLUMN display_order INT UNSIGNED NOT NULL DEFAULT 0 AFTER name,
    ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1 AFTER display_order;

UPDATE restaurant_category
SET category_code = CONCAT('LEGACY_', category_id);

ALTER TABLE restaurant_category
    MODIFY COLUMN category_code VARCHAR(50) NOT NULL,
    ADD CONSTRAINT uk_restaurant_category_code UNIQUE (category_code);

ALTER TABLE restaurant
    RENAME COLUMN account_id TO owner_account_id,
    ALGORITHM=INPLACE;

ALTER TABLE restaurant
    MODIFY COLUMN owner_account_id BIGINT UNSIGNED NOT NULL COMMENT '소유 사업자',
    MODIFY COLUMN status ENUM('OPEN','ACTIVE','INACTIVE','DELETED') NOT NULL DEFAULT 'OPEN',
    ADD COLUMN address_detail VARCHAR(255) NULL AFTER address,
    ADD COLUMN opening_hours VARCHAR(500) NULL AFTER phone,
    ADD COLUMN closed_days VARCHAR(255) NULL AFTER opening_hours,
    ADD COLUMN deleted_at DATETIME(6) NULL AFTER updated_at,
    ADD KEY idx_restaurant_location (latitude, longitude);

UPDATE restaurant SET status = 'ACTIVE' WHERE status = 'OPEN';

ALTER TABLE restaurant
    MODIFY COLUMN status ENUM('ACTIVE','INACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE',
    RENAME INDEX idx_restaurant_account TO idx_restaurant_owner;

ALTER TABLE menu
    ADD COLUMN image_url VARCHAR(500) NULL AFTER description,
    ADD COLUMN representative TINYINT(1) NOT NULL DEFAULT 0 AFTER image_url,
    ADD COLUMN status ENUM('AVAILABLE','SOLD_OUT','INACTIVE') NOT NULL DEFAULT 'AVAILABLE' AFTER representative;

UPDATE menu SET status = 'INACTIVE' WHERE is_deleted = 1;

ALTER TABLE menu
    MODIFY COLUMN description VARCHAR(500) NULL,
    DROP COLUMN is_deleted,
    DROP INDEX idx_menu_restaurant,
    ADD KEY idx_menu_restaurant_status (restaurant_id, status);

ALTER TABLE tag
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

ALTER TABLE restaurant_tag
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

ALTER TABLE restaurant_news
    ADD COLUMN image_url VARCHAR(500) NULL AFTER content,
    ADD COLUMN status ENUM('ACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE' AFTER image_url,
    ADD COLUMN deleted_at DATETIME(6) NULL AFTER updated_at;

UPDATE restaurant_news SET status = 'DELETED', deleted_at = updated_at WHERE is_deleted = 1;

ALTER TABLE restaurant_news
    DROP COLUMN is_deleted,
    DROP INDEX idx_news_restaurant,
    ADD KEY idx_restaurant_news_restaurant_created (restaurant_id, created_at);

CREATE TABLE restaurant_image (
    restaurant_image_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    restaurant_id BIGINT UNSIGNED NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    representative TINYINT(1) NOT NULL DEFAULT 0,
    display_order INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (restaurant_image_id),
    KEY idx_restaurant_image_restaurant (restaurant_id, display_order),
    CONSTRAINT fk_restaurant_image_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurant (restaurant_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='음식점 이미지';

CREATE TABLE menu_submission (
    menu_submission_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    restaurant_id BIGINT UNSIGNED NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    submission_type ENUM('CREATE','UPDATE','DELETE') NOT NULL,
    menu_name VARCHAR(100) NOT NULL,
    proposed_price INT UNSIGNED NULL,
    proposed_description VARCHAR(500) NULL,
    reason VARCHAR(500) NULL,
    status ENUM('PENDING','APPROVED','REJECTED','CANCELED') NOT NULL DEFAULT 'PENDING',
    processed_by BIGINT UNSIGNED NULL,
    processed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (menu_submission_id),
    KEY idx_menu_submission_restaurant (restaurant_id),
    KEY idx_menu_submission_account (account_id),
    KEY idx_menu_submission_status (status, created_at),
    CONSTRAINT fk_menu_submission_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurant (restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_menu_submission_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE RESTRICT,
    CONSTRAINT fk_menu_submission_processor
        FOREIGN KEY (processed_by) REFERENCES account (account_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자 메뉴 제보';

-- 게시판 삭제 상태와 이름을 통일하고 첨부·태그 구조를 추가한다.
RENAME TABLE `comment` TO post_comment;

ALTER TABLE post
    ADD COLUMN status ENUM('ACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE' AFTER view_count,
    ADD COLUMN deleted_at DATETIME(6) NULL AFTER updated_at;

UPDATE post SET status = 'DELETED', deleted_at = updated_at WHERE is_deleted = 1;

ALTER TABLE post
    DROP COLUMN is_deleted,
    DROP INDEX idx_post_created,
    ADD KEY idx_post_list (board_type, status, created_at);

ALTER TABLE post_comment
    ADD COLUMN status ENUM('ACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE' AFTER content,
    ADD COLUMN deleted_at DATETIME(6) NULL AFTER updated_at;

UPDATE post_comment SET status = 'DELETED', deleted_at = updated_at WHERE is_deleted = 1;

ALTER TABLE post_comment
    DROP COLUMN is_deleted,
    DROP INDEX idx_comment_post,
    ADD KEY idx_post_comment_post_created (post_id, created_at),
    RENAME INDEX idx_comment_account TO idx_post_comment_account;

CREATE TABLE post_media (
    post_media_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    post_id BIGINT UNSIGNED NOT NULL,
    media_type ENUM('IMAGE','VIDEO_LINK') NOT NULL,
    media_url VARCHAR(1000) NOT NULL,
    display_order INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (post_media_id),
    KEY idx_post_media_post (post_id, display_order),
    CONSTRAINT fk_post_media_post
        FOREIGN KEY (post_id) REFERENCES post (post_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='게시글 미디어';

CREATE TABLE post_tag (
    post_id BIGINT UNSIGNED NOT NULL,
    tag_id INT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (post_id, tag_id),
    KEY idx_post_tag_tag (tag_id),
    CONSTRAINT fk_post_tag_post
        FOREIGN KEY (post_id) REFERENCES post (post_id) ON DELETE CASCADE,
    CONSTRAINT fk_post_tag_tag
        FOREIGN KEY (tag_id) REFERENCES tag (tag_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='게시글 태그';

-- 리뷰 평점은 DB에서도 1~5 범위를 보장한다.
ALTER TABLE review
    ADD COLUMN status ENUM('ACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE' AFTER content,
    ADD COLUMN deleted_at DATETIME(6) NULL AFTER updated_at,
    ADD CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5);

UPDATE review SET status = 'DELETED', deleted_at = updated_at WHERE is_deleted = 1;

ALTER TABLE review
    DROP COLUMN is_deleted,
    DROP INDEX idx_review_restaurant,
    ADD KEY idx_review_restaurant_created (restaurant_id, created_at);

-- 알림 대상의 의미와 이동 경로를 명시한다.
ALTER TABLE notification
    MODIFY COLUMN type ENUM(
        'COMMENT','POST_LIKE','POST_LIKE_MILESTONE','BUSINESS_APPROVED','BUSINESS_REJECTED'
    ) NOT NULL,
    ADD COLUMN target_type VARCHAR(30) NULL AFTER content,
    CHANGE COLUMN ref_id target_id BIGINT UNSIGNED NULL,
    ADD COLUMN target_url VARCHAR(500) NULL AFTER target_id,
    ADD COLUMN read_at DATETIME(6) NULL AFTER is_read;

UPDATE notification
SET target_type = CASE
        WHEN type IN ('COMMENT','POST_LIKE','POST_LIKE_MILESTONE') THEN 'POST'
        WHEN type IN ('BUSINESS_APPROVED','BUSINESS_REJECTED') THEN 'BUSINESS_APPLICATION'
        ELSE NULL
    END,
    type = CASE WHEN type = 'POST_LIKE' THEN 'POST_LIKE_MILESTONE' ELSE type END,
    read_at = CASE WHEN is_read = 1 THEN created_at ELSE NULL END;

ALTER TABLE notification
    MODIFY COLUMN type ENUM(
        'COMMENT','POST_LIKE_MILESTONE','BUSINESS_APPROVED','BUSINESS_REJECTED'
    ) NOT NULL;

-- 추천 취향, 관리자 이력, 외부 데이터 적재 이력을 추가한다.
CREATE TABLE user_category_preference (
    account_id BIGINT UNSIGNED NOT NULL,
    category_id INT UNSIGNED NOT NULL,
    preference_score DECIMAL(5,4) NOT NULL DEFAULT 1.0000,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (account_id, category_id),
    CONSTRAINT fk_user_category_preference_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_category_preference_category
        FOREIGN KEY (category_id) REFERENCES restaurant_category (category_id) ON DELETE CASCADE,
    CONSTRAINT chk_user_category_preference_score
        CHECK (preference_score BETWEEN 0.0000 AND 1.0000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자 선호 카테고리';

CREATE TABLE user_tag_preference (
    account_id BIGINT UNSIGNED NOT NULL,
    tag_id INT UNSIGNED NOT NULL,
    preference_score DECIMAL(5,4) NOT NULL DEFAULT 1.0000,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (account_id, tag_id),
    CONSTRAINT fk_user_tag_preference_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_tag_preference_tag
        FOREIGN KEY (tag_id) REFERENCES tag (tag_id) ON DELETE CASCADE,
    CONSTRAINT chk_user_tag_preference_score
        CHECK (preference_score BETWEEN 0.0000 AND 1.0000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자 선호 태그';

CREATE TABLE admin_action_log (
    admin_action_log_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    admin_account_id BIGINT UNSIGNED NULL,
    action_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT UNSIGNED NULL,
    reason VARCHAR(500) NULL,
    detail_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (admin_action_log_id),
    KEY idx_admin_action_admin_created (admin_account_id, created_at),
    KEY idx_admin_action_target (target_type, target_id),
    CONSTRAINT fk_admin_action_account
        FOREIGN KEY (admin_account_id) REFERENCES account (account_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관리자 작업 이력';

CREATE TABLE data_import_history (
    import_history_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    source_name VARCHAR(100) NOT NULL,
    source_file_name VARCHAR(255) NULL,
    total_count INT UNSIGNED NOT NULL DEFAULT 0,
    success_count INT UNSIGNED NOT NULL DEFAULT 0,
    failure_count INT UNSIGNED NOT NULL DEFAULT 0,
    status ENUM('RUNNING','SUCCEEDED','FAILED') NOT NULL DEFAULT 'RUNNING',
    error_summary VARCHAR(1000) NULL,
    started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (import_history_id),
    KEY idx_data_import_started (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='외부 음식점 데이터 적재 이력';

SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;
