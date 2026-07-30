# restaurant (사업자·음식점 관리) - 승재(SB) 담당

사업자 신청/승인, 음식점 등록·수정·삭제, 관리자 기능을 여기에 만듭니다.

## 폴더 역할
- `controller/` : API 입구
- `service/`    : 로직
- `domain/`     : 테이블(Entity) - Restaurant, OwnerProfile 등
- `repository/` : DB 접근
- `dto/`        : 요청/응답 데이터 형식

## 참고
- 음식점 Entity는 지도 담당(JS)과 공동으로 확정해야 합니다 (체크리스트 4번 참고).
- 사업자 승인 시 승보(SP)의 권한 추가 기능을 호출합니다.
