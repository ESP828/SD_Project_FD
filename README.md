# 🦆 푸드덕 (FOODUCK)

맛집 지도, 개인화 추천, 커뮤니티와 음식점 관리를 하나로 연결하는 웹 서비스입니다.

현재 프로젝트는 **Spring Boot 애플리케이션 하나에서 REST API와 정적 웹 화면을 함께 제공**합니다.  
이전 문서에 있던 `frontend/`와 `backend/` 분리 구조는 현재 사용하지 않습니다.

## 기술 구성

| 구분 | 기술 |
|---|---|
| Backend | Java 17, Spring Boot 4.0.7, Spring MVC |
| Database | MySQL 8, Spring Data JPA, Flyway |
| Security | Spring Security, JWT, Argon2id, OAuth 2.0 |
| Frontend | HTML, CSS, Vanilla JavaScript (Spring Boot 정적 리소스) |
| Test | JUnit, Spring Security Test, H2 |
| Build | Maven Wrapper |

## 프로젝트 구조

```text
SD_Project_FD/
├─ .github/                         GitHub PR 템플릿
├─ .vscode/                         팀 공통 VS Code 설정
├─ springboot/
│  ├─ src/main/java/com/example/backend/
│  │  ├─ global/                    공통 설정·보안·예외·응답
│  │  ├─ auth/                      회원가입·로그인·OAuth
│  │  ├─ board/                     게시글·댓글·좋아요
│  │  ├─ map/                       지도 공개 설정
│  │  ├─ mypage/                    마이페이지 조회
│  │  ├─ recommendation/            음식점 추천
│  │  ├─ restaurant/                음식점·사업자 영역
│  │  ├─ favorite/                  즐겨찾기 영역
│  │  ├─ review/                    리뷰 영역
│  │  ├─ notification/              알림 영역
│  │  └─ admin/                     관리자 영역
│  ├─ src/main/resources/
│  │  ├─ db/                        Flyway 및 기존 DB 전환 SQL
│  │  ├─ static/                    웹 화면·CSS·JavaScript·이미지
│  │  └─ application*.properties    실행 설정과 예제
│  ├─ src/test/                     단위·통합 테스트
│  ├─ pom.xml
│  ├─ mvnw
│  └─ mvnw.cmd
├─ CONTRIBUTING.md                  협업·브랜치·커밋 규칙
└─ README.md
```

## 현재 구현 상태

| 영역 | 상태 | 주요 내용 |
|---|---|---|
| 공통 기반 | 구현 | 공통 응답, 예외 처리, CORS, Security, JWT |
| 인증 | 구현 | 회원가입, 로그인, Kakao·Naver·Google OAuth |
| 커뮤니티 | 구현 | 게시글, 댓글, 좋아요, 권한 정책 |
| 마이페이지 | 부분 구현 | 사용자 활동 요약 조회 |
| 추천 | 부분 구현 | 추천 미리보기 및 조회 응답 |
| 지도 | 부분 구현 | Kakao Maps 공개 설정과 정적 지도 화면 |
| 음식점·찜·리뷰·알림·관리자 | 골격 | 패키지 구조 중심, 기능 구현 필요 |

`package-info.java`만 있는 영역은 완성된 기능이 아니라 책임과 구현 위치를 먼저 확정한 설계 골격입니다.
파일 안의 Javadoc이 각 폴더의 사용 목적을 설명하므로 빈 폴더로 판단해 삭제하지 않습니다.

구조의 핵심 원칙:

- `auth`, `board`, `restaurant`, `favorite`, `review`, `notification`은 각자의 원본 데이터를 소유합니다.
- `mypage`와 `admin`은 다른 모듈의 데이터를 복제하지 않고 읽기 결과를 조합합니다.
- `map`은 음식점 원본을 만들지 않고 `restaurant`의 위치·상태 정보를 조회합니다.
- 구현 전 패키지는 `.gitkeep` 대신 역할이 명시된 `package-info.java`로 유지합니다.
- `static/images/markers/`의 상태·상세 마커와 로고 심볼·고해상도 캐릭터는 후속 화면을 위한 원본 자산으로 보존합니다.
- `/pages/restaurant/`는 이전 추천 URL을 현재 `/pages/recommendation/`으로 연결하는 호환 경로입니다.

## 사전 준비

- JDK 17 이상
- MySQL 8
- Git

Maven은 별도로 설치하지 않아도 됩니다. 저장소에 포함된 Maven Wrapper를 사용합니다.

```powershell
java -version
git --version
```

## 로컬 실행

### 1. 저장소 받기

```powershell
git clone <저장소-주소>
cd SD_Project_FD\springboot
```

GitHub Desktop을 사용한다면 저장소를 Clone한 뒤 `springboot` 폴더에서 아래 명령을 실행하면 됩니다.

### 2. 필수 환경변수 설정

다음 값은 저장소에 직접 기록하지 않습니다.

```powershell
$env:DB_URL = "jdbc:mysql://<호스트>:<포트>/fooduck?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
$env:DB_USERNAME = "<DB 사용자>"
$env:DB_PASSWORD = "<DB 비밀번호>"
$env:JWT_SECRET = "<UTF-8 기준 32바이트 이상의 임의 문자열>"
```

주의:

- MySQL 스키마 이름은 반드시 `fooduck`이어야 합니다.
- `JWT_SECRET`은 UTF-8 기준 32바이트 이상이어야 합니다.
- 실제 비밀번호, JWT 키, OAuth 키는 GitHub에 올리지 않습니다.
- OAuth와 Kakao Map을 사용할 때만 관련 환경변수를 추가합니다. 전체 키 목록은
  [`application-local-example.properties`](./springboot/src/main/resources/application-local-example.properties)에서 확인할 수 있습니다.

로컬 설정 파일 방식을 선호한다면 예제 파일을 `application-local.properties`로 복사하고
`SPRING_PROFILES_ACTIVE=local`을 설정하세요. 복사한 파일과 `.env`는 Git에서 제외됩니다.

### 3. 애플리케이션 실행

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```bash
./mvnw spring-boot:run
```

정상 실행 후 다음 주소를 확인합니다.

- 홈: <http://localhost:8081/>
- 연결 확인 API: <http://localhost:8081/api/hello>
- 검색: <http://localhost:8081/pages/search/>
- 지도: <http://localhost:8081/pages/map/>
- 커뮤니티: <http://localhost:8081/pages/board/>
- 추천: <http://localhost:8081/pages/recommendation/>
- 마이페이지: <http://localhost:8081/pages/mypage/>
- 사업자 관리: <http://localhost:8081/pages/business/>
- 관리자: <http://localhost:8081/pages/admin/>
- 로그인: <http://localhost:8081/pages/auth/login.html>

종료할 때는 실행 중인 터미널에서 `Ctrl + C`를 누릅니다.

## 데이터베이스

스키마는 JPA 자동 생성이 아니라 SQL 마이그레이션이 관리합니다.

```text
springboot/src/main/resources/db/
├─ migration/
│  ├─ V1__create_fooduck_v2_schema.sql
│  └─ V2__seed_authorities.sql
└─ legacy/
   └─ upgrade_sb_legacy_to_v2.sql
```

- 비어 있는 새 `fooduck` DB: 검토 후 `FLYWAY_ENABLED=true`로 최초 마이그레이션
- 기존 DB: Flyway를 기본값인 `false`로 유지
- 기존 DB 전환: 반드시 백업본에서 `legacy/upgrade_sb_legacy_to_v2.sql`을 먼저 검증
- JPA 기본 설정: `ddl-auto=validate`

`springboot/db-backups/`는 로컬 복구용이며 Git에서 제외됩니다. 실제 계정 데이터가 포함될 수 있으므로
공유 저장소에 올리지 말고 접근이 제한된 별도 백업 위치에서 관리하세요.

DB 작업 전에는 [`db/migration/README.md`](./springboot/src/main/resources/db/migration/README.md)와
[`db/legacy/README.md`](./springboot/src/main/resources/db/legacy/README.md)를 확인하세요.

## 주요 API

| 기능 | 메서드 | 경로 |
|---|---|---|
| 서버 확인 | GET | `/api/hello` |
| 회원가입 | POST | `/api/auth/signup` |
| 로그인 | POST | `/api/auth/login` |
| OAuth 시작 | GET | `/api/auth/oauth/{provider}/login` |
| OAuth 콜백 | GET | `/api/auth/oauth/{provider}/callback` |
| OAuth 티켓 교환 | POST | `/api/auth/oauth/exchange` |
| 게시글 목록 | GET | `/api/board/posts` |
| 인기 게시글 | GET | `/api/board/posts/best` |
| 게시글 상세 | GET | `/api/board/posts/{postId}` |
| 게시글 작성 | POST | `/api/board/posts` |
| 게시글 좋아요 | POST | `/api/board/posts/{postId}/like` |
| 댓글 목록 | GET | `/api/board/posts/{postId}/comments` |
| 댓글 작성 | POST | `/api/board/posts/{postId}/comments` |
| 마이페이지 요약 | GET | `/api/mypage/overview` |
| 추천 목록 | GET | `/api/recommendations` |
| 지도 공개 설정 | GET | `/api/public/map/config` |

게시글·댓글의 수정과 삭제 API도 구현되어 있습니다. 인증이 필요한 요청은 다음 헤더를 사용합니다.

```http
Authorization: Bearer <access-token>
```

## 테스트

```powershell
cd SD_Project_FD\springboot
.\mvnw.cmd test
```

macOS/Linux에서는 `./mvnw test`를 사용합니다.

현재 인증, OAuth 보안, 게시판 Controller·Service·Policy, 데이터베이스 이름 보호 로직을 테스트합니다.

## 협업

브랜치 전략과 커밋 메시지 규칙은 [`CONTRIBUTING.md`](./CONTRIBUTING.md)를 따릅니다.

- `main`, `dev`에 직접 Push하지 않습니다.
- 기능 브랜치에서 작업하고 Pull Request로 병합합니다.
- 커밋 메시지는 `feat:`, `fix:`, `docs:`, `test:`, `chore:` 형식을 사용합니다.

## 보안 주의사항

- `.env`, DB 비밀번호, JWT 키, OAuth client secret은 커밋하지 않습니다.
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`에는 저장소 기본값이 없으므로 실행 환경에서 반드시 주입합니다.
- Kakao JavaScript 키는 브라우저에 노출되므로 Kakao Developers에서 허용 도메인을 제한합니다.
- `target/`, 실행 로그, 로컬 DB 백업과 임시 배포 ZIP은 Git 추적 대상이 아닙니다.
