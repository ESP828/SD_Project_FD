# board

일반·사업자 커뮤니티의 게시글, 댓글, 게시글 좋아요를 담당합니다.

## 현재 구현

- `controller/`: 게시글·댓글 REST API와 인증 계정 처리
- `service/`: 게시글·댓글 CRUD와 좋아요 트랜잭션
- `domain/`: `Post`, `Comment`, `PostLike` 엔티티와 상태·분류 타입
- `repository/`: 게시글·댓글·좋아요 JPA 접근
- `dto/`: 생성·수정 요청과 목록·상세·댓글 응답
- `query/`: 음식점 등 게시판 참조 데이터 조회
- `mapper/`: 엔티티를 API 응답으로 변환
- `policy/`: 작성자·사업자·관리자 권한 판정
- `exception/`: 게시판 기능 예외와 응답 처리

`event/`는 댓글 작성이나 좋아요 임계치 도달을 알림 모듈과 연결하기 위한 의도적인 구현 예정 영역입니다.

공통 응답은 `global.response.ApiResponse`, 현재 로그인 정보는
`global.security.principal.AuthenticatedAccount`를 사용합니다.
