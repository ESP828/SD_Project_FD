-- H2 브라우저 미리보기에서 아직 JPA 엔티티가 없는 후속 모듈의 빈 조회 경계를 만든다.
-- 운영 MySQL 스키마와 샘플 데이터에는 영향을 주지 않으며, 가짜 음식점 데이터도 넣지 않는다.

CREATE TABLE IF NOT EXISTS restaurant_category (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS restaurant (
    restaurant_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_account_id BIGINT NOT NULL,
    category_id INT,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    latitude DECIMAL(10, 7),
    longitude DECIMAL(10, 7),
    status VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS restaurant_image (
    restaurant_image_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    representative BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS menu (
    menu_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    price INT,
    image_url VARCHAR(500),
    representative BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS favorite (
    account_id BIGINT NOT NULL,
    restaurant_id BIGINT NOT NULL,
    PRIMARY KEY (account_id, restaurant_id)
);

CREATE TABLE IF NOT EXISTS review (
    review_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    rating INT NOT NULL,
    status VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS user_category_preference (
    account_id BIGINT NOT NULL,
    category_id INT NOT NULL,
    preference_score DECIMAL(5, 4) NOT NULL,
    PRIMARY KEY (account_id, category_id)
);

CREATE TABLE IF NOT EXISTS notification (
    notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE
);
