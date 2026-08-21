-- FOODUCK 게시판 공지 고정 상태 분리 패치
-- 기존 NOTICE 카테고리 게시글은 자유 이야기 + 상단 고정으로 이관한다.

ALTER TABLE post
    ADD COLUMN is_pinned TINYINT(1) NOT NULL DEFAULT 0 AFTER is_edited;

UPDATE post
SET is_pinned = 1,
    category = 'GENERAL'
WHERE category = 'NOTICE';

CREATE INDEX idx_post_pinned_list
    ON post (board_type, status, is_pinned, created_at);
