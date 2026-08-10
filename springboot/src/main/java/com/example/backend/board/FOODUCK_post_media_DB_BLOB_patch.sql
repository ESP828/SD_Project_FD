-- =====================================================================
-- FOODUCK post_media DB BLOB patch
-- 게시글에 첨부한 사진/동영상 파일을 DB에 직접 저장하기 위해 적용한 변경 기록
--
-- 목적
--   기존 post_media 테이블은 media_url 중심의 미디어 참조 구조였음.
--   게시글 작성 시 업로드한 사진/동영상 원본 파일을 DB에 저장하고,
--   이후 게시글 상세 화면에서 해당 파일의 형식, 이름, 크기 정보를
--   함께 사용할 수 있도록 아래 4개 컬럼을 추가함.
--
-- 변경 대상
--   TABLE: post_media
--
-- 추가 컬럼
--   1. media_data     : 사진/동영상 원본 바이너리 데이터
--   2. mime_type      : image/jpeg, image/png, video/mp4 등의 MIME 형식
--   3. original_name  : 사용자가 업로드한 원본 파일명
--   4. file_size      : 업로드 파일의 바이트 단위 크기
--
-- 주의
--   - 기존 post_media_id, post_id, media_type, media_url,
--     display_order, created_at 컬럼은 변경하지 않음.
--   - 기존 게시글과 미디어의 FK 구조도 변경하지 않음.
--   - NULL을 허용해 기존 media_url 기반 데이터와의 호환성을 유지함.
--   - AFTER 절은 컬럼의 물리적 표시 순서를 정하기 위한 것으로,
--     기능상 필수 조건은 아님.
-- =====================================================================

ALTER TABLE post_media

    -- 실제 사진 또는 동영상 파일의 바이너리 데이터를 저장한다.
    -- 이미지와 동영상처럼 파일 크기가 커질 수 있으므로 LONGBLOB을 사용한다.
    -- 기존 URL 기반 미디어 레코드에는 데이터가 없을 수 있어 NULL을 허용한다.
    ADD COLUMN media_data LONGBLOB NULL AFTER media_url,

    -- 업로드된 파일의 Content-Type 정보를 저장한다.
    -- 예:
    --   image/jpeg
    --   image/png
    --   image/gif
    --   video/mp4
    -- 서버가 DB에서 파일을 다시 내려줄 때 적절한 Content-Type 응답 헤더를
    -- 결정하거나 이미지/동영상을 구분하는 보조 정보로 사용할 수 있다.
    ADD COLUMN mime_type VARCHAR(100) NULL AFTER media_data,

    -- 사용자가 업로드한 당시의 원본 파일명을 저장한다.
    -- 게시글 상세 화면에서 파일명을 표시하거나
    -- 원본 파일 다운로드 시 다운로드 파일명으로 사용할 수 있다.
    ADD COLUMN original_name VARCHAR(255) NULL AFTER mime_type,

    -- 업로드된 원본 파일 크기를 바이트 단위로 저장한다.
    -- 화면에서 파일 크기를 표시하거나 업로드 제한을 확인하고,
    -- 동영상 서버 저장 진행률 계산 시 전체 크기 기준값으로 활용할 수 있다.
    ADD COLUMN file_size BIGINT UNSIGNED NULL AFTER original_name;


-- =====================================================================
-- 적용 결과 확인
--
-- 아래 쿼리로 post_media 테이블에 새 컬럼이 정상적으로 추가되었는지 확인한다.
-- 예상 추가 컬럼:
--   media_data
--   mime_type
--   original_name
--   file_size
-- =====================================================================

SHOW COLUMNS FROM post_media;
