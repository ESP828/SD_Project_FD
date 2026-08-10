-- =====================================================================
-- FOODUCK post_comment reply patch
-- 댓글 답글(1단계 thread) 기능을 위해 실제 DB에 적용할 쿼리 기록
--
-- 목적
--   - 일반 댓글은 parent_comment_id = NULL
--   - 답글은 최상위 부모 댓글의 comment_id를 parent_comment_id에 저장
--   - 답글에 다시 답글을 남겨도 서비스에서 최상위 부모 ID로 평탄화
--   - 부모 댓글이 삭제되면 연결된 답글도 함께 삭제되도록 FK CASCADE 적용
--
-- 주의
--   이 파일은 프로젝트 migration 파일이 아니라 수동 실행/백업용 기록이다.
-- =====================================================================

ALTER TABLE post_comment
    ADD COLUMN parent_comment_id BIGINT UNSIGNED NULL AFTER account_id,
    ADD INDEX idx_post_comment_parent (parent_comment_id),
    ADD CONSTRAINT fk_post_comment_parent
        FOREIGN KEY (parent_comment_id)
        REFERENCES post_comment (comment_id)
        ON DELETE CASCADE;

-- 적용 결과 확인
SHOW COLUMNS FROM post_comment;
SHOW INDEX FROM post_comment;
