-- FOODUCK 베스트 커뮤니티 관리자 수동 지정/제외 패치
-- NULL = 자동 기준, 1 = 관리자 강제 포함, 0 = 관리자 강제 제외

USE foodduck;

SELECT DATABASE();

SHOW COLUMNS FROM post LIKE 'best_override';

-- 위 조회 결과가 없을 때만 아래 ALTER TABLE을 실행합니다.
ALTER TABLE post
    ADD COLUMN best_override TINYINT(1) NULL DEFAULT NULL AFTER is_pinned;

SHOW COLUMNS FROM post LIKE 'best_override';
