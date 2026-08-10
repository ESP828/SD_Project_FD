-- FOODUCK 댓글 이미지 첨부 기능 DB 적용 쿼리
-- 실행 시점: 댓글 이미지 첨부 기능을 적용하기 전에 1회 실행
-- 대상 테이블: post_comment
-- 목적: 댓글당 이미지 1장을 DB LONGBLOB으로 저장
--
-- 프로젝트 내부 migration 파일은 추가하지 않고,
-- 실제 DB에 직접 적용한 뒤 이 파일은 실행 기록/백업용으로 보관한다.

ALTER TABLE post_comment
    ADD COLUMN image_data LONGBLOB NULL AFTER content,
    ADD COLUMN image_mime_type VARCHAR(100) NULL AFTER image_data,
    ADD COLUMN image_original_name VARCHAR(255) NULL AFTER image_mime_type,
    ADD COLUMN image_file_size BIGINT UNSIGNED NULL AFTER image_original_name;

-- 적용 결과 확인
SHOW COLUMNS FROM post_comment;
