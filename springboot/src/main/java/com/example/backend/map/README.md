# map

지도 화면의 공개 설정과 향후 음식점 위치·검색 조회를 담당합니다.

## 현재 구현

- `controller/MapPublicConfigController`: 브라우저용 Kakao Maps JavaScript 키 제공
- `dto/response/MapPublicConfigResponse`: 공개 가능한 지도 설정 응답
- 정적 지도 화면: `src/main/resources/static/pages/map/`

## 설계 의도

- 음식점 원본 데이터는 `restaurant`가 소유합니다.
- `query/`는 활성 음식점의 지도 범위·검색 조건 조회를 담당합니다.
- `integration/kakao/`는 서버에서 사용하는 Kakao Local API 연동 경계입니다.
- `service/`와 `mapper/`는 검색 조건 검증과 지도용 응답 조합을 담당합니다.

JavaScript 키는 브라우저에 공개되므로 Kakao Developers에서 허용 도메인을 제한합니다.
REST API 키와 OAuth secret은 공개 설정 응답에 포함하지 않습니다.
