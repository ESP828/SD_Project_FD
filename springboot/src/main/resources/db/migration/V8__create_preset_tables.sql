CREATE TABLE preset (
    preset_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL,
    summary VARCHAR(255) NOT NULL,
    description TEXT NULL,
    image_url VARCHAR(500) NULL,
    category VARCHAR(50) NOT NULL,
    view_count INT UNSIGNED NOT NULL DEFAULT 0,
    display_order INT UNSIGNED NOT NULL DEFAULT 0,
    status ENUM('ACTIVE', 'INACTIVE', 'DELETED') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (preset_id),
    KEY idx_preset_category_status_order (category, status, display_order),
    KEY idx_preset_status_order (status, display_order, preset_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='상황별 맛집 프리셋';

CREATE TABLE preset_restaurant (
    preset_id BIGINT UNSIGNED NOT NULL,
    restaurant_id BIGINT UNSIGNED NOT NULL,
    display_order INT UNSIGNED NOT NULL DEFAULT 0,
    description VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (preset_id, restaurant_id),
    KEY idx_preset_restaurant_restaurant (restaurant_id),
    KEY idx_preset_restaurant_order (preset_id, display_order, restaurant_id),
    CONSTRAINT fk_preset_restaurant_preset
        FOREIGN KEY (preset_id) REFERENCES preset (preset_id) ON DELETE CASCADE,
    CONSTRAINT fk_preset_restaurant_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurant (restaurant_id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='프리셋별 음식점';
