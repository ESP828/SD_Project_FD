# Foodduck DB migration

이 폴더는 **비어 있는 MySQL 8 데이터베이스**에 적용하는 Flyway 마이그레이션입니다.

- `V1__create_fooduck_v2_schema.sql`: 개선된 전체 스키마
- `V2__seed_authorities.sql`: 기본 권한 기준값

SB가 사용하던 기존 `fooduck` 스키마에는 이 V1을 바로 적용하지 않습니다. 기존 데이터베이스를
개선할 때는 `../legacy/upgrade_sb_legacy_to_v2.sql`을 먼저 별도 백업본에서 검증한 후 적용합니다.

기본 애플리케이션 설정에서는 사고 방지를 위해 Flyway를 비활성화했습니다. 새 빈 DB에 설치할
때만 `FLYWAY_ENABLED=true`를 명시합니다.

`V18__create_public_restaurant_enrichment.sql`은 공식 데이터의 출처와 엄격 매칭 결과를
기존 식당 데이터와 분리해 저장합니다. 운영 DB에서는 `ai/import_public_enrichment.py
--apply --create-schema`가 해당 DDL과 검증된 보강 행을 순서대로 적용하며,
`public_restaurant`와 `menu`는 수정하지 않습니다.

`V19__create_public_restaurant_official_evidence.sql`은 엄격 매칭된 서울관광재단 품질정보와
메뉴 가격 요약을 별도 테이블에 저장합니다. 운영 DB에서는
`ai/import_seoul_tourism_evidence.py --apply --create-schema`로 원본 해시, 전체 행 수,
식당 ID 연결, 예상 영향 행 수를 확인한 뒤 업서트하며 기존 음식점 및 메뉴 행은 수정하지 않습니다.
