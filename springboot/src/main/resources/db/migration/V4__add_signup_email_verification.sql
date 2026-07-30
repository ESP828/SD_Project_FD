SET NAMES utf8mb4;

CREATE TABLE signup_email_verification (
    email_verification_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    code VARCHAR(6) NOT NULL,
    verified TINYINT(1) NOT NULL DEFAULT 0,
    attempt_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (email_verification_id),
    CONSTRAINT uk_signup_email_verification_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회원가입 이메일 인증번호';
