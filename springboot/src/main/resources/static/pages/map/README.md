# Kakao Map 정적 화면

Spring Boot 정적 HTML/CSS/JavaScript만 사용한다.

- `index.html` [1]: 검색·카테고리·결과 목록·지도 영역
- `map.js` [2]: 공개 설정 조회, Kakao SDK 로드, Places 검색, 현재 위치, 목록/마커 선택
- `map.css` [3]: 데스크톱 분할 화면과 모바일 적층 화면

필수 환경변수는 `KAKAO_MAPS_JAVASCRIPT_KEY`다. JavaScript 키는 브라우저에 공개되는
키이므로 Kakao Developers 콘솔에서 허용 도메인을 제한해야 한다. REST API 키와
OAuth client secret은 공개 설정 API나 정적 파일에 넣지 않는다.

현재 구현:

1. URL의 `q` 검색어 수신
2. 기본 검색어 `서울 맛집` 실행
3. Kakao Places 결과 최대 15개를 목록과 커스텀 SVG 마커로 표시
4. 목록/마커 선택 상태와 정보창 동기화
5. 현재 위치 이동과 지도 중심 주변 카테고리 검색

프로젝트 DB의 음식점 상태·사업자 정보·찜 여부는 restaurant/mypage 조회 API가
구현된 뒤 Kakao 장소 식별값과 연결한다.
