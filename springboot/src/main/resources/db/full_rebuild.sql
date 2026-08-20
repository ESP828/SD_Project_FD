-- ============================================================
-- Fooduck DB 전체 재생성 스크립트 (인수인계용)
-- ============================================================
-- 이 파일은 Flyway가 자동으로 실행하지 않는다 (db/migration 밖에 있음).
-- 기존 마이그레이션과 현재 개발 기능에 필요한 스키마를 한 번에 재생성할 수 있도록
-- 합쳐 둔 파일이며, 리뷰 사진·동영상용 review_media 테이블도 포함한다.
--
-- 사용법:
--   1) 이 파일이 스키마 foodduck을 알아서 만든다 (CREATE DATABASE IF NOT EXISTS).
--   2) DBeaver 등에서 이 파일 전체를 실행한다.
--   3) 애플리케이션 .env의 FLYWAY_ENABLED는 false로 유지한다.
--      (스키마를 이 파일로 이미 다 만들었으므로 Flyway가 V1부터 다시
--       실행하면 "테이블이 이미 있다"는 에러가 난다.)
--   4) public_restaurant 데이터(공공데이터 음식점, 약 376MB)는 이 파일에
--      포함되어 있지 않다. 관리자 계정으로 로그인한 뒤
--      POST /api/admin/restaurants/public-data/sync?startPage=1&maxPages=500
--      를 여러 번 호출해서 다시 채워야 한다(공공데이터 API 일일 호출 제한 때문에
--      한 번에 전국 데이터를 다 못 받을 수 있음).
--
-- 참고: preset_tag / preset_favorite (아래 32, 33번)은 V9 마이그레이션
-- 내용인데, 기존 운영 DB에는 아직 반영이 안 된 상태였다(V6 버전 번호 충돌로
-- 서버가 못 떠서). 새로 만드는 DB는 이 두 테이블도 정상적으로 갖게 된다.
-- ============================================================

CREATE DATABASE IF NOT EXISTS `foodduck`
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `foodduck`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- 1. authority
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `authority`;
CREATE TABLE `authority` (
    `authority_id` SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `authority_code` VARCHAR(30) NOT NULL,
    `authority_name` VARCHAR(50) NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`authority_id`),
    CONSTRAINT `uk_authority_code` UNIQUE (`authority_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='권한 기준 정보';

-- ------------------------------------------------------------
-- 2. account
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `account`;
CREATE TABLE `account` (
    `account_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `login_id` VARCHAR(50) NULL COMMENT '일반 로그인 아이디; 소셜 전용 계정은 NULL 가능',
    `email` VARCHAR(254) NULL,
    `nickname` VARCHAR(30) NOT NULL,
    `gender` ENUM('UNSPECIFIED','MALE','FEMALE','OTHER') NOT NULL DEFAULT 'UNSPECIFIED',
    `birth_date` DATE NULL,
    `profile_image_url` VARCHAR(500) NULL,
    `email_verified` TINYINT(1) NOT NULL DEFAULT 0,
    `profile_completed` TINYINT(1) NOT NULL DEFAULT 0,
    `status` ENUM('ACTIVE','INACTIVE','SUSPENDED','WITHDRAWN') NOT NULL DEFAULT 'ACTIVE',
    `last_login_at` DATETIME(6) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `deleted_at` DATETIME(6) NULL,
    PRIMARY KEY (`account_id`),
    CONSTRAINT `uk_account_login_id` UNIQUE (`login_id`),
    CONSTRAINT `uk_account_email` UNIQUE (`email`),
    CONSTRAINT `uk_account_nickname` UNIQUE (`nickname`),
    KEY `idx_account_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='계정 기본 정보';

-- ------------------------------------------------------------
-- 3. account_credential
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `account_credential`;
CREATE TABLE `account_credential` (
    `account_id` BIGINT UNSIGNED NOT NULL,
    `password_hash` VARCHAR(255) NOT NULL,
    `failed_login_count` SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    `locked_until` DATETIME(6) NULL,
    `password_changed_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`account_id`),
    CONSTRAINT `fk_account_credential_account`
        FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='일반 로그인 자격 정보';

-- ------------------------------------------------------------
-- 4. account_authority
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `account_authority`;
CREATE TABLE `account_authority` (
    `account_id` BIGINT UNSIGNED NOT NULL,
    `authority_id` SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`account_id`, `authority_id`),
    KEY `idx_account_authority_authority` (`authority_id`),
    CONSTRAINT `fk_account_authority_account`
        FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_account_authority_authority`
        FOREIGN KEY (`authority_id`) REFERENCES `authority` (`authority_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='계정별 권한';

-- ------------------------------------------------------------
-- 5. social_account
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `social_account`;
CREATE TABLE `social_account` (
    `social_account_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `account_id` BIGINT UNSIGNED NOT NULL,
    `provider` ENUM('KAKAO','NAVER','GOOGLE') NOT NULL,
    `provider_user_id` VARCHAR(191) NOT NULL,
    `provider_email` VARCHAR(254) NULL COMMENT '공급자가 마지막으로 제공한 이메일 스냅샷',
    `provider_nickname` VARCHAR(100) NULL COMMENT '공급자가 마지막으로 제공한 닉네임 스냅샷',
    `connected_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`social_account_id`),
    CONSTRAINT `uk_social_provider_user` UNIQUE (`provider`, `provider_user_id`),
    CONSTRAINT `uk_social_account_provider` UNIQUE (`account_id`, `provider`),
    KEY `idx_social_account_account` (`account_id`),
    CONSTRAINT `fk_social_account_account`
        FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='소셜 로그인 연결';

-- ------------------------------------------------------------
-- 6. refresh_token
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `refresh_token`;
CREATE TABLE `refresh_token` (
    `refresh_token_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `account_id` BIGINT UNSIGNED NOT NULL,
    `token_hash` CHAR(64) NOT NULL COMMENT '원문 토큰이 아닌 SHA-256 해시',
    `expires_at` DATETIME(6) NOT NULL,
    `revoked_at` DATETIME(6) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`refresh_token_id`),
    CONSTRAINT `uk_refresh_token_hash` UNIQUE (`token_hash`),
    KEY `idx_refresh_token_account_expiry` (`account_id`, `expires_at`),
    CONSTRAINT `fk_refresh_token_account`
        FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='리프레시 토큰 해시';

-- ------------------------------------------------------------
-- 7. email_verification
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `email_verification`;
CREATE TABLE `email_verification` (
    `verification_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `account_id` BIGINT UNSIGNED NULL,
    `email` VARCHAR(254) NOT NULL,
    `purpose` ENUM('SIGN_UP','PASSWORD_RESET','EMAIL_CHANGE') NOT NULL,
    `code_hash` CHAR(64) NOT NULL,
    `expires_at` DATETIME(6) NOT NULL,
    `verified_at` DATETIME(6) NULL,
    `attempt_count` SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`verification_id`),
    KEY `idx_email_verification_lookup` (`email`, `purpose`, `expires_at`),
    KEY `idx_email_verification_account` (`account_id`),
    CONSTRAINT `fk_email_verification_account`
        FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='이메일 인증';

-- ------------------------------------------------------------
-- 8. signup_email_verification (V4)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `signup_email_verification`;
CREATE TABLE `signup_email_verification` (
    `email_verification_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `email` VARCHAR(254) NOT NULL,
    `code` VARCHAR(6) NOT NULL,
    `verified` TINYINT(1) NOT NULL DEFAULT 0,
    `attempt_count` SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    `expires_at` DATETIME(6) NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`email_verification_id`),
    CONSTRAINT `uk_signup_email_verification_email` UNIQUE (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회원가입 이메일 인증번호';

-- ------------------------------------------------------------
-- 9. business_application (V1 + V6의 opened_at 포함)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `business_application`;
CREATE TABLE `business_application` (
    `application_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `account_id` BIGINT UNSIGNED NOT NULL,
    `business_name` VARCHAR(100) NOT NULL,
    `business_number` VARCHAR(20) NOT NULL,
    `representative_name` VARCHAR(50) NOT NULL,
    `opened_at` DATE NULL COMMENT '사업자등록증상 개업일자(국세청 진위확인용)',
    `contact` VARCHAR(30) NOT NULL,
    `reason` VARCHAR(500) NULL,
    `status` ENUM('PENDING','APPROVED','REJECTED','CANCELED') NOT NULL DEFAULT 'PENDING',
    `reject_reason` VARCHAR(500) NULL,
    `processed_by` BIGINT UNSIGNED NULL,
    `processed_at` DATETIME(6) NULL,
    `canceled_at` DATETIME(6) NULL,
    `pending_account_id` BIGINT UNSIGNED
        GENERATED ALWAYS AS (
            CASE WHEN status = 'PENDING' THEN account_id ELSE NULL END
        ) STORED,
    `pending_business_number` VARCHAR(20)
        GENERATED ALWAYS AS (
            CASE WHEN status = 'PENDING' THEN business_number ELSE NULL END
        ) STORED,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`application_id`),
    KEY `idx_business_application_account` (`account_id`),
    KEY `idx_business_application_status_created` (`status`, `created_at`),
    KEY `idx_business_application_processor` (`processed_by`),
    CONSTRAINT `uk_business_application_pending_account` UNIQUE (`pending_account_id`),
    CONSTRAINT `uk_business_application_pending_number` UNIQUE (`pending_business_number`),
    CONSTRAINT `fk_business_application_account`
        FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_business_application_processor`
        FOREIGN KEY (`processed_by`) REFERENCES `account` (`account_id`) ON DELETE SET NULL,
    CONSTRAINT `chk_business_application_rejection`
        CHECK (status <> 'REJECTED' OR reject_reason IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사업자 권한 신청';

-- ------------------------------------------------------------
-- 10. business_profile
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `business_profile`;
CREATE TABLE `business_profile` (
    `business_profile_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `account_id` BIGINT UNSIGNED NOT NULL,
    `application_id` BIGINT UNSIGNED NULL,
    `business_name` VARCHAR(100) NOT NULL,
    `business_number` VARCHAR(20) NOT NULL,
    `representative_name` VARCHAR(50) NOT NULL,
    `contact` VARCHAR(30) NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`business_profile_id`),
    CONSTRAINT `uk_business_profile_account` UNIQUE (`account_id`),
    CONSTRAINT `uk_business_profile_number` UNIQUE (`business_number`),
    KEY `idx_business_profile_application` (`application_id`),
    CONSTRAINT `fk_business_profile_account`
        FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_business_profile_application`
        FOREIGN KEY (`application_id`) REFERENCES `business_application` (`application_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='승인된 사업자 프로필';

-- ------------------------------------------------------------
-- 11. restaurant_category
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `restaurant_category`;
CREATE TABLE `restaurant_category` (
    `category_id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `parent_id` INT UNSIGNED NULL,
    `category_code` VARCHAR(50) NOT NULL,
    `name` VARCHAR(50) NOT NULL,
    `display_order` INT UNSIGNED NOT NULL DEFAULT 0,
    `active` TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (`category_id`),
    CONSTRAINT `uk_restaurant_category_code` UNIQUE (`category_code`),
    KEY `idx_restaurant_category_parent` (`parent_id`),
    CONSTRAINT `fk_restaurant_category_parent`
        FOREIGN KEY (`parent_id`) REFERENCES `restaurant_category` (`category_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='음식점 카테고리';

-- ------------------------------------------------------------
-- 12. restaurant
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `restaurant`;
CREATE TABLE `restaurant` (
    `restaurant_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `owner_account_id` BIGINT UNSIGNED NOT NULL,
    `category_id` INT UNSIGNED NULL,
    `name` VARCHAR(100) NOT NULL,
    `address` VARCHAR(255) NOT NULL,
    `address_detail` VARCHAR(255) NULL,
    `latitude` DECIMAL(10,7) NULL,
    `longitude` DECIMAL(10,7) NULL,
    `phone` VARCHAR(30) NULL,
    `opening_hours` VARCHAR(500) NULL,
    `closed_days` VARCHAR(255) NULL,
    `description` TEXT NULL,
    `status` ENUM('ACTIVE','INACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `deleted_at` DATETIME(6) NULL,
    PRIMARY KEY (`restaurant_id`),
    KEY `idx_restaurant_owner` (`owner_account_id`),
    KEY `idx_restaurant_category_status` (`category_id`, `status`),
    KEY `idx_restaurant_name` (`name`),
    KEY `idx_restaurant_location` (`latitude`, `longitude`),
    CONSTRAINT `fk_restaurant_owner`
        FOREIGN KEY (`owner_account_id`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_restaurant_category`
        FOREIGN KEY (`category_id`) REFERENCES `restaurant_category` (`category_id`) ON DELETE SET NULL,
    CONSTRAINT `chk_restaurant_latitude` CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT `chk_restaurant_longitude` CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='음식점';

-- ------------------------------------------------------------
-- 13. public_restaurant (Flyway 마이그레이션 밖에서 만들어져 있던 테이블 +
--     V5의 풀텍스트 인덱스를 CREATE TABLE에 합쳐 넣음)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `public_restaurant`;
CREATE TABLE `public_restaurant` (
    `public_restaurant_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `external_store_id` VARCHAR(30) NOT NULL COMMENT '공공데이터 상가업소번호(bizesId)',
    `name` VARCHAR(150) NOT NULL,
    `branch_name` VARCHAR(100) NULL,
    `category_large_code` VARCHAR(10) NULL,
    `category_large_name` VARCHAR(50) NULL,
    `category_medium_code` VARCHAR(10) NULL,
    `category_medium_name` VARCHAR(50) NULL,
    `category_small_code` VARCHAR(10) NULL,
    `category_small_name` VARCHAR(50) NULL,
    `sido_name` VARCHAR(30) NULL,
    `sigungu_name` VARCHAR(30) NULL,
    `road_address` VARCHAR(255) NULL,
    `lot_address` VARCHAR(255) NULL,
    `latitude` DECIMAL(10,7) NULL,
    `longitude` DECIMAL(10,7) NULL,
    `data_ym` VARCHAR(6) NULL COMMENT '데이터 기준연월(YYYYMM)',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`public_restaurant_id`),
    CONSTRAINT `uk_public_restaurant_external_store_id` UNIQUE (`external_store_id`),
    KEY `idx_public_restaurant_location` (`latitude`, `longitude`),
    KEY `idx_public_restaurant_name` (`name`),
    FULLTEXT KEY `ft_public_restaurant_search`
        (`name`, `category_large_name`, `category_medium_name`, `category_small_name`, `road_address`, `lot_address`)
        WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='공공데이터 출처 음식점(상가업소정보)';

-- ------------------------------------------------------------
-- 14. menu
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `menu`;
CREATE TABLE `menu` (
    `menu_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `restaurant_id` BIGINT UNSIGNED NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `price` INT UNSIGNED NULL,
    `description` VARCHAR(500) NULL,
    `image_url` VARCHAR(500) NULL,
    `representative` TINYINT(1) NOT NULL DEFAULT 0,
    `status` ENUM('AVAILABLE','SOLD_OUT','INACTIVE') NOT NULL DEFAULT 'AVAILABLE',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`menu_id`),
    KEY `idx_menu_restaurant_status` (`restaurant_id`, `status`),
    CONSTRAINT `fk_menu_restaurant`
        FOREIGN KEY (`restaurant_id`) REFERENCES `restaurant` (`restaurant_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='음식점 메뉴';

-- ------------------------------------------------------------
-- 15. tag
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag` (
    `tag_id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(50) NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`tag_id`),
    CONSTRAINT `uk_tag_name` UNIQUE (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='검색·추천 태그';

-- ------------------------------------------------------------
-- 16. restaurant_tag
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `restaurant_tag`;
CREATE TABLE `restaurant_tag` (
    `restaurant_id` BIGINT UNSIGNED NOT NULL,
    `tag_id` INT UNSIGNED NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`restaurant_id`, `tag_id`),
    KEY `idx_restaurant_tag_tag` (`tag_id`),
    CONSTRAINT `fk_restaurant_tag_restaurant`
        FOREIGN KEY (`restaurant_id`) REFERENCES `restaurant` (`restaurant_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_restaurant_tag_tag`
        FOREIGN KEY (`tag_id`) REFERENCES `tag` (`tag_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='음식점 태그';

-- ------------------------------------------------------------
-- 17. restaurant_news
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `restaurant_news`;
CREATE TABLE `restaurant_news` (
    `news_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `restaurant_id` BIGINT UNSIGNED NOT NULL,
    `title` VARCHAR(200) NOT NULL,
    `content` TEXT NOT NULL,
    `image_url` VARCHAR(500) NULL,
    `status` ENUM('ACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `deleted_at` DATETIME(6) NULL,
    PRIMARY KEY (`news_id`),
    KEY `idx_restaurant_news_restaurant_created` (`restaurant_id`, `created_at`),
    CONSTRAINT `fk_restaurant_news_restaurant`
        FOREIGN KEY (`restaurant_id`) REFERENCES `restaurant` (`restaurant_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='음식점 소식';

-- ------------------------------------------------------------
-- 18. restaurant_image
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `restaurant_image`;
CREATE TABLE `restaurant_image` (
    `restaurant_image_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `restaurant_id` BIGINT UNSIGNED NOT NULL,
    `image_url` VARCHAR(500) NOT NULL,
    `representative` TINYINT(1) NOT NULL DEFAULT 0,
    `display_order` INT UNSIGNED NOT NULL DEFAULT 0,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`restaurant_image_id`),
    KEY `idx_restaurant_image_restaurant` (`restaurant_id`, `display_order`),
    CONSTRAINT `fk_restaurant_image_restaurant`
        FOREIGN KEY (`restaurant_id`) REFERENCES `restaurant` (`restaurant_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='음식점 이미지';

-- ------------------------------------------------------------
-- 19. menu_submission
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `menu_submission`;
CREATE TABLE `menu_submission` (
    `menu_submission_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `restaurant_id` BIGINT UNSIGNED NOT NULL,
    `account_id` BIGINT UNSIGNED NOT NULL,
    `submission_type` ENUM('CREATE','UPDATE','DELETE') NOT NULL,
    `menu_name` VARCHAR(100) NOT NULL,
    `proposed_price` INT UNSIGNED NULL,
    `proposed_description` VARCHAR(500) NULL,
    `reason` VARCHAR(500) NULL,
    `status` ENUM('PENDING','APPROVED','REJECTED','CANCELED') NOT NULL DEFAULT 'PENDING',
    `processed_by` BIGINT UNSIGNED NULL,
    `processed_at` DATETIME(6) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`menu_submission_id`),
    KEY `idx_menu_submission_restaurant` (`restaurant_id`),
    KEY `idx_menu_submission_account` (`account_id`),
    KEY `idx_menu_submission_status` (`status`, `created_at`),
    CONSTRAINT `fk_menu_submission_restaurant`
        FOREIGN KEY (`restaurant_id`) REFERENCES `restaurant` (`restaurant_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_menu_submission_account`
        FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_menu_submission_processor`
        FOREIGN KEY (`processed_by`) REFERENCES `account` (`account_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자 메뉴 제보';

-- ------------------------------------------------------------
-- 20. post
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `post`;
CREATE TABLE `post` (
    `post_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `account_id` BIGINT UNSIGNED NOT NULL,
    `restaurant_id` BIGINT UNSIGNED NULL,
    `board_type` ENUM('GENERAL','BUSINESS') NOT NULL DEFAULT 'GENERAL',
    `category` VARCHAR(50) NULL,
    `title` VARCHAR(200) NOT NULL,
    `content` TEXT NOT NULL,
    `like_count` INT UNSIGNED NOT NULL DEFAULT 0,
    `view_count` INT UNSIGNED NOT NULL DEFAULT 0,
    `status` ENUM('ACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `deleted_at` DATETIME(6) NULL,
    PRIMARY KEY (`post_id`),
    KEY `idx_post_account` (`account_id`),
    KEY `idx_post_restaurant` (`restaurant_id`),
    KEY `idx_post_list` (`board_type`, `status`, `created_at`),
    KEY `idx_post_title` (`title`),
    CONSTRAINT `fk_post_account`
        FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_post_restaurant`
        FOREIGN KEY (`restaurant_id`) REFERENCES `restaurant` (`restaurant_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='커뮤니티 게시글';

-- ------------------------------------------------------------
-- 21. post_comment
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `post_comment`;
CREATE TABLE `post_comment` (
    `comment_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `post_id` BIGINT UNSIGNED NOT NULL,
    `account_id` BIGINT UNSIGNED NOT NULL,
    `content` VARCHAR(1000) NOT NULL,
    `status` ENUM('ACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `deleted_at` DATETIME(6) NULL,
    PRIMARY KEY (`comment_id`),
    KEY `idx_post_comment_post_created` (`post_id`, `created_at`),
    KEY `idx_post_comment_account` (`account_id`),
    CONSTRAINT `fk_post_comment_post`
        FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_post_comment_account`
        FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='게시글 댓글';

-- ------------------------------------------------------------
-- 22. post_like
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `post_like`;
CREATE TABLE `post_like` (
    `post_id` BIGINT UNSIGNED NOT NULL,
    `account_id` BIGINT UNSIGNED NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`post_id`, `account_id`),
    KEY `idx_post_like_account` (`account_id`),
    CONSTRAINT `fk_post_like_post`
        FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_post_like_account`
        FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='게시글 추천';

-- ------------------------------------------------------------
-- 23. post_media
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `post_media`;
CREATE TABLE `post_media` (
    `post_media_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `post_id` BIGINT UNSIGNED NOT NULL,
    `media_type` ENUM('IMAGE','VIDEO_LINK') NOT NULL,
    `media_url` VARCHAR(1000) NOT NULL,
    `display_order` INT UNSIGNED NOT NULL DEFAULT 0,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`post_media_id`),
    KEY `idx_post_media_post` (`post_id`, `display_order`),
    CONSTRAINT `fk_post_media_post`
        FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='게시글 미디어';

-- ------------------------------------------------------------
-- 24. post_tag
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `post_tag`;
CREATE TABLE `post_tag` (
    `post_id` BIGINT UNSIGNED NOT NULL,
    `tag_id` INT UNSIGNED NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`post_id`, `tag_id`),
    KEY `idx_post_tag_tag` (`tag_id`),
    CONSTRAINT `fk_post_tag_post`
        FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_post_tag_tag`
        FOREIGN KEY (`tag_id`) REFERENCES `tag` (`tag_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='게시글 태그';

-- ------------------------------------------------------------
-- 25. favorite
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `favorite`;
CREATE TABLE `favorite` (
    `account_id` BIGINT UNSIGNED NOT NULL,
    `restaurant_id` BIGINT UNSIGNED NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`account_id`, `restaurant_id`),
    KEY `idx_favorite_restaurant` (`restaurant_id`),
    CONSTRAINT `fk_favorite_account`
        FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_favorite_restaurant`
        FOREIGN KEY (`restaurant_id`) REFERENCES `restaurant` (`restaurant_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='음식점 찜';

-- ------------------------------------------------------------
-- 26. review (V1 + V10의 public_restaurant_id 확장 포함)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `review`;
CREATE TABLE `review` (
    `review_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `restaurant_id` BIGINT UNSIGNED NULL,
    `public_restaurant_id` BIGINT UNSIGNED NULL,
    `account_id` BIGINT UNSIGNED NOT NULL,
    `rating` TINYINT UNSIGNED NOT NULL,
    `content` VARCHAR(1000) NULL,
    `status` ENUM('ACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `deleted_at` DATETIME(6) NULL,
    PRIMARY KEY (`review_id`),
    KEY `idx_review_restaurant_created` (`restaurant_id`, `created_at`),
    KEY `idx_review_public_restaurant` (`public_restaurant_id`, `status`),
    KEY `idx_review_account` (`account_id`),
    CONSTRAINT `fk_review_restaurant`
        FOREIGN KEY (`restaurant_id`) REFERENCES `restaurant` (`restaurant_id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_review_public_restaurant`
        FOREIGN KEY (`public_restaurant_id`) REFERENCES `public_restaurant` (`public_restaurant_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_review_account`
        FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_review_rating` CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='음식점 리뷰';

-- ------------------------------------------------------------
-- 26-1. review_media
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `review_media`;
CREATE TABLE `review_media` (
    `review_media_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `review_id` BIGINT UNSIGNED NOT NULL,
    `media_type` VARCHAR(20) NOT NULL,
    `media_data` LONGBLOB NOT NULL,
    `mime_type` VARCHAR(100) NOT NULL,
    `original_name` VARCHAR(255) NOT NULL,
    `file_size` BIGINT UNSIGNED NOT NULL,
    `display_order` INT UNSIGNED NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`review_media_id`),
    INDEX `idx_review_media_review` (`review_id`, `display_order`, `review_media_id`),
    CONSTRAINT `fk_review_media_review`
        FOREIGN KEY (`review_id`)
        REFERENCES `review` (`review_id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 27. notification
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification` (
    `notification_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `account_id` BIGINT UNSIGNED NOT NULL,
    `type` ENUM('COMMENT','POST_LIKE_MILESTONE','BUSINESS_APPROVED','BUSINESS_REJECTED') NOT NULL,
    `content` VARCHAR(255) NOT NULL,
    `target_type` VARCHAR(30) NULL,
    `target_id` BIGINT UNSIGNED NULL,
    `target_url` VARCHAR(500) NULL,
    `is_read` TINYINT(1) NOT NULL DEFAULT 0,
    `read_at` DATETIME(6) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`notification_id`),
    KEY `idx_notification_account_unread` (`account_id`, `is_read`, `created_at`),
    CONSTRAINT `fk_notification_account`
        FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자 알림';

-- ------------------------------------------------------------
-- 28. user_category_preference
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user_category_preference`;
CREATE TABLE `user_category_preference` (
    `account_id` BIGINT UNSIGNED NOT NULL,
    `category_id` INT UNSIGNED NOT NULL,
    `preference_score` DECIMAL(5,4) NOT NULL DEFAULT 1.0000,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`account_id`, `category_id`),
    CONSTRAINT `fk_user_category_preference_account`
        FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_category_preference_category`
        FOREIGN KEY (`category_id`) REFERENCES `restaurant_category` (`category_id`) ON DELETE CASCADE,
    CONSTRAINT `chk_user_category_preference_score`
        CHECK (preference_score BETWEEN 0.0000 AND 1.0000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자 선호 카테고리';

-- ------------------------------------------------------------
-- 29. user_tag_preference
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user_tag_preference`;
CREATE TABLE `user_tag_preference` (
    `account_id` BIGINT UNSIGNED NOT NULL,
    `tag_id` INT UNSIGNED NOT NULL,
    `preference_score` DECIMAL(5,4) NOT NULL DEFAULT 1.0000,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`account_id`, `tag_id`),
    CONSTRAINT `fk_user_tag_preference_account`
        FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_tag_preference_tag`
        FOREIGN KEY (`tag_id`) REFERENCES `tag` (`tag_id`) ON DELETE CASCADE,
    CONSTRAINT `chk_user_tag_preference_score`
        CHECK (preference_score BETWEEN 0.0000 AND 1.0000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자 선호 태그';

-- ------------------------------------------------------------
-- 30. admin_action_log
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `admin_action_log`;
CREATE TABLE `admin_action_log` (
    `admin_action_log_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `admin_account_id` BIGINT UNSIGNED NULL,
    `action_type` VARCHAR(50) NOT NULL,
    `target_type` VARCHAR(50) NOT NULL,
    `target_id` BIGINT UNSIGNED NULL,
    `reason` VARCHAR(500) NULL,
    `detail_json` JSON NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`admin_action_log_id`),
    KEY `idx_admin_action_admin_created` (`admin_account_id`, `created_at`),
    KEY `idx_admin_action_target` (`target_type`, `target_id`),
    CONSTRAINT `fk_admin_action_account`
        FOREIGN KEY (`admin_account_id`) REFERENCES `account` (`account_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관리자 작업 이력';

-- ------------------------------------------------------------
-- 31. data_import_history
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `data_import_history`;
CREATE TABLE `data_import_history` (
    `import_history_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `source_name` VARCHAR(100) NOT NULL,
    `source_file_name` VARCHAR(255) NULL,
    `total_count` INT UNSIGNED NOT NULL DEFAULT 0,
    `success_count` INT UNSIGNED NOT NULL DEFAULT 0,
    `failure_count` INT UNSIGNED NOT NULL DEFAULT 0,
    `status` ENUM('RUNNING','SUCCEEDED','FAILED') NOT NULL DEFAULT 'RUNNING',
    `error_summary` VARCHAR(1000) NULL,
    `started_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `completed_at` DATETIME(6) NULL,
    PRIMARY KEY (`import_history_id`),
    KEY `idx_data_import_started` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='외부 음식점 데이터 적재 이력';

-- ------------------------------------------------------------
-- 32. preset (V8, 원래 파일명은 V6이었으나 번호 충돌로 재번호 매김)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `preset`;
CREATE TABLE `preset` (
    `preset_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(100) NOT NULL,
    `summary` VARCHAR(255) NOT NULL,
    `description` TEXT NULL,
    `image_url` VARCHAR(500) NULL,
    `category` VARCHAR(50) NOT NULL,
    `view_count` INT UNSIGNED NOT NULL DEFAULT 0,
    `display_order` INT UNSIGNED NOT NULL DEFAULT 0,
    `status` ENUM('ACTIVE','INACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `deleted_at` DATETIME(6) NULL,
    PRIMARY KEY (`preset_id`),
    KEY `idx_preset_category_status_order` (`category`, `status`, `display_order`),
    KEY `idx_preset_status_order` (`status`, `display_order`, `preset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='상황별 맛집 프리셋';

-- ------------------------------------------------------------
-- 33. preset_restaurant (V8)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `preset_restaurant`;
CREATE TABLE `preset_restaurant` (
    `preset_id` BIGINT UNSIGNED NOT NULL,
    `restaurant_id` BIGINT UNSIGNED NOT NULL,
    `display_order` INT UNSIGNED NOT NULL DEFAULT 0,
    `description` VARCHAR(255) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`preset_id`, `restaurant_id`),
    KEY `idx_preset_restaurant_restaurant` (`restaurant_id`),
    KEY `idx_preset_restaurant_order` (`preset_id`, `display_order`, `restaurant_id`),
    CONSTRAINT `fk_preset_restaurant_preset`
        FOREIGN KEY (`preset_id`) REFERENCES `preset` (`preset_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_preset_restaurant_restaurant`
        FOREIGN KEY (`restaurant_id`) REFERENCES `restaurant` (`restaurant_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='프리셋별 음식점';

-- ------------------------------------------------------------
-- 34. preset_favorite (V9, 원래 파일명은 V7 — 기존 운영 DB엔 아직 미반영)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `preset_favorite`;
CREATE TABLE `preset_favorite` (
    `account_id` BIGINT UNSIGNED NOT NULL,
    `preset_id` BIGINT UNSIGNED NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`account_id`, `preset_id`),
    KEY `idx_preset_favorite_preset` (`preset_id`, `created_at`),
    CONSTRAINT `fk_preset_favorite_account`
        FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_preset_favorite_preset`
        FOREIGN KEY (`preset_id`) REFERENCES `preset` (`preset_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자별 프리셋 찜';

-- ------------------------------------------------------------
-- 35. preset_tag (V9, 원래 파일명은 V7 — 기존 운영 DB엔 아직 미반영)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `preset_tag`;
CREATE TABLE `preset_tag` (
    `preset_id` BIGINT UNSIGNED NOT NULL,
    `tag_id` INT UNSIGNED NOT NULL,
    `display_order` INT UNSIGNED NOT NULL DEFAULT 0,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`preset_id`, `tag_id`),
    KEY `idx_preset_tag_tag` (`tag_id`, `preset_id`),
    KEY `idx_preset_tag_order` (`preset_id`, `display_order`, `tag_id`),
    CONSTRAINT `fk_preset_tag_preset`
        FOREIGN KEY (`preset_id`) REFERENCES `preset` (`preset_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_preset_tag_tag`
        FOREIGN KEY (`tag_id`) REFERENCES `tag` (`tag_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='프리셋 상황 태그';

SET FOREIGN_KEY_CHECKS = 1;

-- ------------------------------------------------------------
-- 시드 데이터 (V2)
-- ------------------------------------------------------------
-- authority_id는 AUTO_INCREMENT 컬럼이라, sql_mode에 NO_AUTO_VALUE_ON_ZERO가
-- 없으면 명시적으로 넣은 0이 "자동생성해라"는 의미로 해석돼 실제로는 1이
-- 들어가 버린다(그래서 다음 줄의 1과 충돌해 Duplicate entry 에러가 났다).
SET @OLD_SQL_MODE = @@SESSION.sql_mode;
SET SESSION sql_mode = (SELECT CONCAT(@@SESSION.sql_mode, ',NO_AUTO_VALUE_ON_ZERO'));

INSERT INTO `authority` (`authority_id`, `authority_code`, `authority_name`)
VALUES
    (0, 'ROLE_USER', '일반 사용자'),
    (1, 'ROLE_BUSINESS', '사업자'),
    (2, 'ROLE_ADMIN', '관리자');

SET SESSION sql_mode = @OLD_SQL_MODE;

-- ------------------------------------------------------------
-- 트리거 (V3 최종본): account INSERT 시 일반 사용자 권한 자동 부여
-- ------------------------------------------------------------
DROP TRIGGER IF EXISTS `trg_account_default_authority`;
DELIMITER $$
CREATE TRIGGER `trg_account_default_authority`
AFTER INSERT ON `account`
FOR EACH ROW
INSERT IGNORE INTO `account_authority` (`account_id`, `authority_id`, `created_at`)
VALUES (NEW.account_id, 0, CURRENT_TIMESTAMP(6))$$
DELIMITER ;
