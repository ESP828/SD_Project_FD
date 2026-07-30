# Foodduck DB migration

이 폴더는 **비어 있는 MySQL 8 데이터베이스**에 적용하는 Flyway 마이그레이션입니다.

- `V1__create_fooduck_v2_schema.sql`: 개선된 전체 스키마
- `V2__seed_authorities.sql`: 기본 권한 기준값

SB가 사용하던 기존 `fooduck` 스키마에는 이 V1을 바로 적용하지 않습니다. 기존 데이터베이스를
개선할 때는 `../legacy/upgrade_sb_legacy_to_v2.sql`을 먼저 별도 백업본에서 검증한 후 적용합니다.

기본 애플리케이션 설정에서는 사고 방지를 위해 Flyway를 비활성화했습니다. 새 빈 DB에 설치할
때만 `FLYWAY_ENABLED=true`를 명시합니다.
