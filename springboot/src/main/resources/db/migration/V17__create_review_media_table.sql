CREATE TABLE review_media (
    review_media_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    review_id BIGINT UNSIGNED NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    media_data LONGBLOB NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    file_size BIGINT UNSIGNED NOT NULL,
    display_order INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (review_media_id),
    INDEX idx_review_media_review (review_id, display_order, review_media_id),
    CONSTRAINT fk_review_media_review
        FOREIGN KEY (review_id)
        REFERENCES review (review_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
