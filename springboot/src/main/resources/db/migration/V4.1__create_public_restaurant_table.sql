-- 공공데이터포털 소상공인시장진흥공단 상가업소정보 API로 적재하는 음식점 테이블.
-- 원래는 Flyway 없이(개발 초기 ddl-auto=update 시절) 만들어져 있던 테이블이라 마이그레이션에 빠져 있었다.
-- 새 DB에서도 V5(풀텍스트 인덱스)가 정상 동작하려면 이 테이블이 V5보다 먼저 생성되어야 한다.
-- 컬럼 구성은 com.example.backend.restaurant.domain.entity.PublicRestaurant 엔티티와 1:1로 맞췄다.
CREATE TABLE public_restaurant (
    public_restaurant_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_store_id VARCHAR(30) NOT NULL COMMENT '공공데이터 상가업소번호(bizesId)',
    name VARCHAR(150) NOT NULL,
    branch_name VARCHAR(100) NULL,
    category_large_code VARCHAR(10) NULL,
    category_large_name VARCHAR(50) NULL,
    category_medium_code VARCHAR(10) NULL,
    category_medium_name VARCHAR(50) NULL,
    category_small_code VARCHAR(10) NULL,
    category_small_name VARCHAR(50) NULL,
    sido_name VARCHAR(30) NULL,
    sigungu_name VARCHAR(30) NULL,
    road_address VARCHAR(255) NULL,
    lot_address VARCHAR(255) NULL,
    latitude DECIMAL(10,7) NULL,
    longitude DECIMAL(10,7) NULL,
    data_ym VARCHAR(6) NULL COMMENT '데이터 기준연월(YYYYMM)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (public_restaurant_id),
    CONSTRAINT uk_public_restaurant_external_store_id UNIQUE (external_store_id),
    KEY idx_public_restaurant_location (latitude, longitude),
    KEY idx_public_restaurant_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='공공데이터 출처 음식점(상가업소정보)';
