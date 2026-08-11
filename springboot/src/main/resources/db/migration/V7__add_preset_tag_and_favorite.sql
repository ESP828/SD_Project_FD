CREATE TABLE preset_favorite (
    account_id BIGINT UNSIGNED NOT NULL,
    preset_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (account_id, preset_id),
    KEY idx_preset_favorite_preset (preset_id, created_at),
    CONSTRAINT fk_preset_favorite_account
        FOREIGN KEY (account_id) REFERENCES account (account_id) ON DELETE CASCADE,
    CONSTRAINT fk_preset_favorite_preset
        FOREIGN KEY (preset_id) REFERENCES preset (preset_id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='사용자별 프리셋 찜';

CREATE TABLE preset_tag (
    preset_id BIGINT UNSIGNED NOT NULL,
    tag_id INT UNSIGNED NOT NULL,
    display_order INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (preset_id, tag_id),
    KEY idx_preset_tag_tag (tag_id, preset_id),
    KEY idx_preset_tag_order (preset_id, display_order, tag_id),
    CONSTRAINT fk_preset_tag_preset
        FOREIGN KEY (preset_id) REFERENCES preset (preset_id) ON DELETE CASCADE,
    CONSTRAINT fk_preset_tag_tag
        FOREIGN KEY (tag_id) REFERENCES tag (tag_id) ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='프리셋 상황 태그';
