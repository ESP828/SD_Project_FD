CREATE TABLE preset_image (
    preset_image_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    preset_id BIGINT UNSIGNED NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (preset_image_id),
    UNIQUE KEY uq_preset_image_preset (preset_id),
    CONSTRAINT fk_preset_image_preset
        FOREIGN KEY (preset_id) REFERENCES preset (preset_id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='프리셋 대표 이미지 파일 메타데이터';
