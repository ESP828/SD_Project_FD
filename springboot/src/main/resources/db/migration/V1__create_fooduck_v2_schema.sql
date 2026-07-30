SET NAMES utf8mb4;

CREATE TABLE authority (
    authority_id SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    authority_code VARCHAR(30) NOT NULL,
    authority_name VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (authority_id),
    CONSTRAINT uk_authority_code UNIQUE (authority_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='권한 기준 정보';

CREATE TABLE account (
    account_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    login_id VARCHAR(50) NULL COMMENT '일반 로그인 아이디; 소셜 전용 계정은 NULL 가능',
    email VARCHAR(254) NULL,
    nickname VARCHAR(30) NOT NULL,
    gender ENUM('UNSPECIFIED','MALE','FEMALE','OTHER') NOT NULL DEFAULT 'UNSPECIFIED',
    birth_date DATE NULL,
    profile_image_url VARCHAR(500) NULL,
    email_verified TINYINT(1) NOT NULL DEFAULT 0,
    profile_completed TINYINT(1) NOT NULL DEFAULT 0,
    status ENUM('ACTIVE','INACTIVE','SUSPENDED','WITHDRAWN') NOT NULL DEFAULT 'ACTIVE',
    last_login_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (account_id),
    CONSTRAINT uk_account_login_id UNIQUE (login_id),
    CONSTRAINT uk_account_email UNIQUE (email),
    CONSTRAINT uk_account_nickname UNIQUE (nickname),
    KEY idx_account_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='계정 기본 정보';

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

CREATE TABLE account_authority (
    account_id BIGINT UNSIGNED NOT NULL,
    authority_id SMALLINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (account_id, authority_id),
    KEY idx_account_authority_authority (authority_id),
    CONSTRAINT fk_account_authority_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE CASCADE,
    CONSTRAINT fk_account_authority_authority
        FOREIGN KEY (authority_id) REFERENCES authority (authority_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='계정별 권한';

CREATE TABLE social_account (
    social_account_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    provider ENUM('KAKAO','NAVER','GOOGLE') NOT NULL,
    provider_user_id VARCHAR(191) NOT NULL,
    provider_email VARCHAR(254) NULL COMMENT '공급자가 마지막으로 제공한 이메일 스냅샷',
    provider_nickname VARCHAR(100) NULL COMMENT '공급자가 마지막으로 제공한 닉네임 스냅샷',
    connected_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (social_account_id),
    CONSTRAINT uk_social_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT uk_social_account_provider UNIQUE (account_id, provider),
    KEY idx_social_account_account (account_id),
    CONSTRAINT fk_social_account_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='소셜 로그인 연결';

CREATE TABLE refresh_token (
    refresh_token_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64) NOT NULL COMMENT '원문 토큰이 아닌 SHA-256 해시',
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (refresh_token_id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    KEY idx_refresh_token_account_expiry (account_id, expires_at),
    CONSTRAINT fk_refresh_token_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='리프레시 토큰 해시';

CREATE TABLE email_verification (
    verification_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NULL,
    email VARCHAR(254) NOT NULL,
    purpose ENUM('SIGN_UP','PASSWORD_RESET','EMAIL_CHANGE') NOT NULL,
    code_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    verified_at DATETIME(6) NULL,
    attempt_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (verification_id),
    KEY idx_email_verification_lookup (email, purpose, expires_at),
    KEY idx_email_verification_account (account_id),
    CONSTRAINT fk_email_verification_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='이메일 인증';

CREATE TABLE business_application (
    application_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    business_name VARCHAR(100) NOT NULL,
    business_number VARCHAR(20) NOT NULL,
    representative_name VARCHAR(50) NOT NULL,
    contact VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NULL,
    status ENUM('PENDING','APPROVED','REJECTED','CANCELED') NOT NULL DEFAULT 'PENDING',
    reject_reason VARCHAR(500) NULL,
    processed_by BIGINT UNSIGNED NULL,
    processed_at DATETIME(6) NULL,
    canceled_at DATETIME(6) NULL,
    pending_account_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (
            CASE WHEN status = 'PENDING' THEN account_id ELSE NULL END
        ) STORED,
    pending_business_number VARCHAR(20)
        GENERATED ALWAYS AS (
            CASE WHEN status = 'PENDING' THEN business_number ELSE NULL END
        ) STORED,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (application_id),
    KEY idx_business_application_account (account_id),
    KEY idx_business_application_status_created (status, created_at),
    KEY idx_business_application_processor (processed_by),
    CONSTRAINT uk_business_application_pending_account UNIQUE (pending_account_id),
    CONSTRAINT uk_business_application_pending_number UNIQUE (pending_business_number),
    CONSTRAINT fk_business_application_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE RESTRICT,
    CONSTRAINT fk_business_application_processor
        FOREIGN KEY (processed_by) REFERENCES account (account_id) ON DELETE SET NULL,
    CONSTRAINT chk_business_application_rejection
        CHECK (status <> 'REJECTED' OR reject_reason IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사업자 권한 신청';

CREATE TABLE business_profile (
    business_profile_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    application_id BIGINT UNSIGNED NULL,
    business_name VARCHAR(100) NOT NULL,
    business_number VARCHAR(20) NOT NULL,
    representative_name VARCHAR(50) NOT NULL,
    contact VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (business_profile_id),
    CONSTRAINT uk_business_profile_account UNIQUE (account_id),
    CONSTRAINT uk_business_profile_number UNIQUE (business_number),
    KEY idx_business_profile_application (application_id),
    CONSTRAINT fk_business_profile_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE RESTRICT,
    CONSTRAINT fk_business_profile_application
        FOREIGN KEY (application_id) REFERENCES business_application (application_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='승인된 사업자 프로필';

CREATE TABLE restaurant_category (
    category_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_id INT UNSIGNED NULL,
    category_code VARCHAR(50) NOT NULL,
    name VARCHAR(50) NOT NULL,
    display_order INT UNSIGNED NOT NULL DEFAULT 0,
    active TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (category_id),
    CONSTRAINT uk_restaurant_category_code UNIQUE (category_code),
    KEY idx_restaurant_category_parent (parent_id),
    CONSTRAINT fk_restaurant_category_parent
        FOREIGN KEY (parent_id) REFERENCES restaurant_category (category_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='음식점 카테고리';

CREATE TABLE restaurant (
    restaurant_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    owner_account_id BIGINT UNSIGNED NOT NULL,
    category_id INT UNSIGNED NULL,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    address_detail VARCHAR(255) NULL,
    latitude DECIMAL(10,7) NULL,
    longitude DECIMAL(10,7) NULL,
    phone VARCHAR(30) NULL,
    opening_hours VARCHAR(500) NULL,
    closed_days VARCHAR(255) NULL,
    description TEXT NULL,
    status ENUM('ACTIVE','INACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (restaurant_id),
    KEY idx_restaurant_owner (owner_account_id),
    KEY idx_restaurant_category_status (category_id, status),
    KEY idx_restaurant_name (name),
    KEY idx_restaurant_location (latitude, longitude),
    CONSTRAINT fk_restaurant_owner
        FOREIGN KEY (owner_account_id) REFERENCES account (account_id) ON DELETE RESTRICT,
    CONSTRAINT fk_restaurant_category
        FOREIGN KEY (category_id) REFERENCES restaurant_category (category_id) ON DELETE SET NULL,
    CONSTRAINT chk_restaurant_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_restaurant_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='음식점';

CREATE TABLE menu (
    menu_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    restaurant_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(100) NOT NULL,
    price INT UNSIGNED NULL,
    description VARCHAR(500) NULL,
    image_url VARCHAR(500) NULL,
    representative TINYINT(1) NOT NULL DEFAULT 0,
    status ENUM('AVAILABLE','SOLD_OUT','INACTIVE') NOT NULL DEFAULT 'AVAILABLE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (menu_id),
    KEY idx_menu_restaurant_status (restaurant_id, status),
    CONSTRAINT fk_menu_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurant (restaurant_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='음식점 메뉴';

CREATE TABLE tag (
    tag_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (tag_id),
    CONSTRAINT uk_tag_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='검색·추천 태그';

CREATE TABLE restaurant_tag (
    restaurant_id BIGINT UNSIGNED NOT NULL,
    tag_id INT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (restaurant_id, tag_id),
    KEY idx_restaurant_tag_tag (tag_id),
    CONSTRAINT fk_restaurant_tag_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurant (restaurant_id) ON DELETE CASCADE,
    CONSTRAINT fk_restaurant_tag_tag
        FOREIGN KEY (tag_id) REFERENCES tag (tag_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='음식점 태그';

CREATE TABLE restaurant_news (
    news_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    restaurant_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    image_url VARCHAR(500) NULL,
    status ENUM('ACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (news_id),
    KEY idx_restaurant_news_restaurant_created (restaurant_id, created_at),
    CONSTRAINT fk_restaurant_news_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurant (restaurant_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='음식점 소식';

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

CREATE TABLE post (
    post_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    restaurant_id BIGINT UNSIGNED NULL,
    board_type ENUM('GENERAL','BUSINESS') NOT NULL DEFAULT 'GENERAL',
    category VARCHAR(50) NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    like_count INT UNSIGNED NOT NULL DEFAULT 0,
    view_count INT UNSIGNED NOT NULL DEFAULT 0,
    status ENUM('ACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (post_id),
    KEY idx_post_account (account_id),
    KEY idx_post_restaurant (restaurant_id),
    KEY idx_post_list (board_type, status, created_at),
    KEY idx_post_title (title),
    CONSTRAINT fk_post_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE RESTRICT,
    CONSTRAINT fk_post_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurant (restaurant_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='커뮤니티 게시글';

CREATE TABLE post_comment (
    comment_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    post_id BIGINT UNSIGNED NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    content VARCHAR(1000) NOT NULL,
    status ENUM('ACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (comment_id),
    KEY idx_post_comment_post_created (post_id, created_at),
    KEY idx_post_comment_account (account_id),
    CONSTRAINT fk_post_comment_post
        FOREIGN KEY (post_id) REFERENCES post (post_id) ON DELETE CASCADE,
    CONSTRAINT fk_post_comment_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='게시글 댓글';

CREATE TABLE post_like (
    post_id BIGINT UNSIGNED NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (post_id, account_id),
    KEY idx_post_like_account (account_id),
    CONSTRAINT fk_post_like_post
        FOREIGN KEY (post_id) REFERENCES post (post_id) ON DELETE CASCADE,
    CONSTRAINT fk_post_like_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='게시글 추천';

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

CREATE TABLE favorite (
    account_id BIGINT UNSIGNED NOT NULL,
    restaurant_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (account_id, restaurant_id),
    KEY idx_favorite_restaurant (restaurant_id),
    CONSTRAINT fk_favorite_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE CASCADE,
    CONSTRAINT fk_favorite_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurant (restaurant_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='음식점 찜';

CREATE TABLE review (
    review_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    restaurant_id BIGINT UNSIGNED NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    rating TINYINT UNSIGNED NOT NULL,
    content VARCHAR(1000) NULL,
    status ENUM('ACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (review_id),
    KEY idx_review_restaurant_created (restaurant_id, created_at),
    KEY idx_review_account (account_id),
    CONSTRAINT fk_review_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurant (restaurant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE RESTRICT,
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='음식점 리뷰';

CREATE TABLE notification (
    notification_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    type ENUM('COMMENT','POST_LIKE_MILESTONE','BUSINESS_APPROVED','BUSINESS_REJECTED') NOT NULL,
    content VARCHAR(255) NOT NULL,
    target_type VARCHAR(30) NULL,
    target_id BIGINT UNSIGNED NULL,
    target_url VARCHAR(500) NULL,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (notification_id),
    KEY idx_notification_account_unread (account_id, is_read, created_at),
    CONSTRAINT fk_notification_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자 알림';

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
