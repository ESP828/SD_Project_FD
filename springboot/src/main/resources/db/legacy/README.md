# SB legacy schema upgrade

`upgrade_sb_legacy_to_v2.sql`은 사용자가 제공한
`dump-fooduck-202607281242.sql` 구조를 개선 스키마로 옮기는 수동 마이그레이션입니다.

주의:

1. `dump-mysql-*.sql`과 `dump-performance_schema-*.sql`은 MySQL 시스템 스키마이므로 사용하지 않습니다.
2. 운영 또는 팀 공용 DB에 바로 실행하지 말고 전체 백업본을 새 데이터베이스에 복원해 먼저 검증합니다.
3. 스크립트는 제공된 SB 스키마를 정확한 시작점으로 가정합니다.
4. 성공 후 애플리케이션 엔티티 검증과 핵심 행 수 비교를 수행합니다.
5. 이미 일부 구조를 수동 변경한 DB에는 중복 적용하지 않습니다.
