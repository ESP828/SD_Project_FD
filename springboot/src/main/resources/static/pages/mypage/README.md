# 마이페이지 정적 화면

- `index.html`: 프로필, 권한, 활동 요약과 후속 기능 진입 영역
- `mypage.js`: `GET /api/mypage/overview` 호출과 응답 렌더링
- `mypage.css`: 마이페이지 전용 반응형 스타일
- `detail.html`, `detail.js`, `detail.css`: 좌측 프로필·서브메뉴와 선택 활동 목록

로그인 토큰이 없으면 로그인 안내를 표시하고, 만료되거나 유효하지 않은 토큰은 공통 API 도구가 제거합니다.
요약 카드는 `detail.html?tab=reviews`와 같은 주소로 이동하며 선택한 서브메뉴를 바로 엽니다.
