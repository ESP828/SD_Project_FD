# 테스트 리소스

- `application-test.properties`: H2 인메모리 DB, JWT/OAuth 테스트 값과 test 프로필 설정
- `schema.sql`: JPA 엔티티 외에 테스트 조회가 요구하는 테이블·뷰 초기화
- `db/sb-legacy-sample-data.sql`: 기존 SB 스키마 전환 SQL을 검증하기 위한 표본 데이터

테스트는 운영 MySQL이나 외부 OAuth 공급자에 연결하지 않습니다.
실서비스 접속 정보와 API 키를 이 폴더에 추가하지 마세요.
