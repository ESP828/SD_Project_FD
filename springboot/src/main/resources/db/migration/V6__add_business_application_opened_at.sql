ALTER TABLE business_application
    ADD COLUMN opened_at DATE NULL COMMENT '사업자등록증상 개업일자(국세청 진위확인용)' AFTER representative_name;
