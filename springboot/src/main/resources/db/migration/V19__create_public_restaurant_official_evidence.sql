CREATE TABLE public_restaurant_quality_evidence (
    quality_evidence_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_restaurant_id BIGINT UNSIGNED NOT NULL,
    source_code VARCHAR(80) NOT NULL,
    source_record_id VARCHAR(80) NOT NULL,
    source_restaurant_name VARCHAR(255) NOT NULL,
    source_branch_name VARCHAR(150) NULL,
    source_region_name VARCHAR(100) NOT NULL,
    match_method VARCHAR(50) NOT NULL,
    match_confidence DECIMAL(5,4) NOT NULL,
    award_description VARCHAR(1000) NULL,
    rti_score DECIMAL(10,6) NULL,
    online_progress TINYINT(1) NULL,
    acceptance_score DECIMAL(10,6) NULL,
    popularity_score DECIMAL(10,6) NULL,
    tripadvisor_rating DECIMAL(4,2) NULL,
    ctrip_rating DECIMAL(4,2) NULL,
    naver_rating DECIMAL(4,2) NULL,
    raw_record JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (quality_evidence_id),
    UNIQUE KEY uq_public_restaurant_quality_source_record (source_code, source_record_id),
    UNIQUE KEY uq_public_restaurant_quality_restaurant_source (public_restaurant_id, source_code),
    KEY idx_public_restaurant_quality_award_rating (award_description(100), naver_rating),
    CONSTRAINT fk_public_restaurant_quality_restaurant
        FOREIGN KEY (public_restaurant_id) REFERENCES public_restaurant (public_restaurant_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_public_restaurant_quality_source
        FOREIGN KEY (source_code) REFERENCES public_data_source (source_code)
        ON DELETE RESTRICT,
    CONSTRAINT chk_public_restaurant_quality_match_confidence
        CHECK (match_confidence >= 0 AND match_confidence <= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Official restaurant quality evidence linked through a strict source restaurant match';

CREATE TABLE public_restaurant_menu_evidence (
    menu_evidence_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_restaurant_id BIGINT UNSIGNED NOT NULL,
    source_code VARCHAR(80) NOT NULL,
    source_restaurant_id VARCHAR(80) NOT NULL,
    source_restaurant_name VARCHAR(255) NOT NULL,
    source_branch_name VARCHAR(150) NULL,
    match_method VARCHAR(50) NOT NULL,
    match_confidence DECIMAL(5,4) NOT NULL,
    menu_count INT UNSIGNED NOT NULL,
    priced_menu_count INT UNSIGNED NOT NULL,
    minimum_menu_price INT UNSIGNED NULL,
    typical_menu_price INT UNSIGNED NULL,
    maximum_menu_price INT UNSIGNED NULL,
    menu_names VARCHAR(4000) NULL,
    vegan_labeled_menu_available TINYINT(1) NOT NULL DEFAULT 0,
    vegetarian_labeled_menu_available TINYINT(1) NOT NULL DEFAULT 0,
    gluten_free_labeled_menu_available TINYINT(1) NOT NULL DEFAULT 0,
    price_examples JSON NOT NULL,
    raw_summary JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (menu_evidence_id),
    UNIQUE KEY uq_public_restaurant_menu_source_record (source_code, source_restaurant_id),
    UNIQUE KEY uq_public_restaurant_menu_restaurant_source (public_restaurant_id, source_code),
    KEY idx_public_restaurant_menu_typical_price (typical_menu_price),
    KEY idx_public_restaurant_menu_dietary
        (vegan_labeled_menu_available, vegetarian_labeled_menu_available,
         gluten_free_labeled_menu_available),
    CONSTRAINT fk_public_restaurant_menu_evidence_restaurant
        FOREIGN KEY (public_restaurant_id) REFERENCES public_restaurant (public_restaurant_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_public_restaurant_menu_evidence_source
        FOREIGN KEY (source_code) REFERENCES public_data_source (source_code)
        ON DELETE RESTRICT,
    CONSTRAINT chk_public_restaurant_menu_match_confidence
        CHECK (match_confidence >= 0 AND match_confidence <= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Aggregated official menu and price evidence without changing the existing menu table';
