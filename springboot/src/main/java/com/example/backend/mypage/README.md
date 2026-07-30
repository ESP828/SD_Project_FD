# mypage

계정, 권한, 게시판, 찜, 리뷰, 알림의 읽기 결과를 조합하는 마이페이지 모듈입니다.

## 현재 구현

- `controller/MyPageController`: 인증 사용자 요약 API
- `service/MyPageService`: 조회 흐름 조정
- `query/MyPageActivityQueryRepository`: 계정별 활동 개수 집계
- `dto/response/MyPageOverviewResponse`: 프로필·권한·활동 요약 응답

## 설계 의도

- URL에서 임의 계정 번호를 받지 않고 인증된 계정만 조회합니다.
- 마이페이지 전용 도메인 엔티티나 Repository를 만들지 않습니다.
- 원본 데이터의 쓰기는 `auth`, `board`, `favorite`, `review`, `notification` 등 소유 모듈에 위임합니다.
- `assembler/`는 여러 모듈의 결과를 마이페이지 응답으로 조립하기 위한 구현 예정 영역입니다.

정적 화면은 `src/main/resources/static/pages/mypage/`에 있습니다.
