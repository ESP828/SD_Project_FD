-- FOODUCK 리뷰 사진·동영상 저장 테이블
--
-- 기존 foodduck DB에 리뷰 미디어 저장 테이블을 추가한다.
-- spring.jpa.hibernate.ddl-auto=validate 환경에서는 서버 실행 전에 한 번 적용한다.
-- 리뷰 완전 삭제 시 첨부 미디어도 함께 삭제되도록 ON DELETE CASCADE를 사용한다.

CREATE TABLE foodduck.review_media (
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
        REFERENCES foodduck.review (review_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
