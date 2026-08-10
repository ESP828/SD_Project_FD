-- FOODUCK 게시글/댓글 실제 수정 여부 표시용 컬럼 추가
-- 프로젝트 내부 마이그레이션 파일이 아니라 DB에 수동 적용하고 보관하기 위한 백업 SQL입니다.

ALTER TABLE post
    ADD COLUMN is_edited TINYINT(1) NOT NULL DEFAULT 0 AFTER updated_at;

ALTER TABLE post_comment
    ADD COLUMN is_edited TINYINT(1) NOT NULL DEFAULT 0 AFTER updated_at;

-- 적용 확인
SHOW COLUMNS FROM post LIKE 'is_edited';
SHOW COLUMNS FROM post_comment LIKE 'is_edited';
