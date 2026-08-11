ALTER TABLE preset
    ADD COLUMN preset_image_id BIGINT UNSIGNED NULL
        COMMENT '대표 이미지 메타데이터 참조' AFTER is_public,
    ADD UNIQUE KEY uq_preset_preset_image (preset_image_id),
    ADD CONSTRAINT fk_preset_preset_image
        FOREIGN KEY (preset_image_id)
        REFERENCES preset_image (preset_image_id)
        ON DELETE SET NULL;

UPDATE preset p
JOIN preset_image pi ON pi.preset_id = p.preset_id
SET p.preset_image_id = pi.preset_image_id
WHERE p.preset_image_id IS NULL;
