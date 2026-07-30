# 커뮤니티 정적 화면

게시글 목록, 상세, 작성·수정과 댓글·좋아요 기능을 Spring Boot 게시판 API에 연결합니다.

- `index.html`, `board.js`: 목록·검색·분류·페이지 처리
- `detail.html`, `detail.js`: 게시글 상세, 댓글, 좋아요, 수정·삭제 진입
- `write.html`, `write.js`: 게시글 생성·수정
- `board-shared.js`: 게시판 공통 DOM·표시 도구
- `board.css`: 세 화면의 공통 스타일

읽기 API는 공개이며 작성·수정·삭제·좋아요 요청에는 JWT가 필요합니다.
화면의 버튼 노출은 편의 기능일 뿐이며 최종 권한은 서버의 `BoardAccessPolicy`가 판정합니다.
