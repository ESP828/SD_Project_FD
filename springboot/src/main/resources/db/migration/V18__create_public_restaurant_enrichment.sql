CREATE TABLE public_data_source (
    source_code VARCHAR(80) NOT NULL,
    provider_name VARCHAR(150) NOT NULL,
    dataset_name VARCHAR(255) NOT NULL,
    source_page_url VARCHAR(1000) NOT NULL,
    download_url VARCHAR(1000) NOT NULL,
    license_name VARCHAR(150) NOT NULL,
    source_published_on DATE NULL,
    retrieved_at DATETIME(6) NOT NULL,
    raw_file_sha256 CHAR(64) NOT NULL,
    raw_row_count INT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (source_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Provenance for verified public restaurant enrichment data';

CREATE TABLE public_restaurant_enrichment (
    enrichment_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_restaurant_id BIGINT UNSIGNED NOT NULL,
    source_code VARCHAR(80) NOT NULL,
    source_record_id VARCHAR(80) NOT NULL,
    source_restaurant_name VARCHAR(255) NOT NULL,
    source_branch_name VARCHAR(150) NULL,
    source_region_name VARCHAR(100) NOT NULL,
    source_status VARCHAR(30) NULL,
    match_method VARCHAR(50) NOT NULL,
    match_confidence DECIMAL(5,4) NOT NULL,
    parking_available TINYINT(1) NULL,
    wifi_available TINYINT(1) NULL,
    playroom_available TINYINT(1) NULL,
    multilingual_menu_available TINYINT(1) NULL,
    delivery_available TINYINT(1) NULL,
    smart_order_available TINYINT(1) NULL,
    restroom_info VARCHAR(500) NULL,
    closed_days VARCHAR(500) NULL,
    opening_hours VARCHAR(1000) NULL,
    reservation_info VARCHAR(1000) NULL,
    homepage_url VARCHAR(1000) NULL,
    nearby_landmark_name VARCHAR(255) NULL,
    representative_menu VARCHAR(1000) NULL,
    hashtags VARCHAR(1000) NULL,
    area_info VARCHAR(1000) NULL,
    raw_record JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (enrichment_id),
    UNIQUE KEY uq_public_restaurant_enrichment_source_record (source_code, source_record_id),
    UNIQUE KEY uq_public_restaurant_enrichment_restaurant_source (public_restaurant_id, source_code),
    KEY idx_public_restaurant_enrichment_amenities
        (parking_available, playroom_available, multilingual_menu_available),
    CONSTRAINT fk_public_restaurant_enrichment_restaurant
        FOREIGN KEY (public_restaurant_id) REFERENCES public_restaurant (public_restaurant_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_public_restaurant_enrichment_source
        FOREIGN KEY (source_code) REFERENCES public_data_source (source_code)
        ON DELETE RESTRICT,
    CONSTRAINT chk_public_restaurant_enrichment_match_confidence
        CHECK (match_confidence >= 0 AND match_confidence <= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Verified public attributes matched without changing base restaurant rows';
