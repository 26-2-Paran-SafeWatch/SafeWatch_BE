아래 내용을 통째로 복사해서 새 Claude Code 세션에 붙여넣으세요.
(이 안내 3줄은 빼고, --- 아래부터 끝까지 넣으면 됩니다.)

---

# SafeWatch 백엔드 인수인계

너는 이제부터 이 프로젝트를 이어서 맡는다. 아래 내용을 전제로 작업해라.

## 0. 작업 규칙 (중요, 반드시 지킬 것)

1. **코드를 쓰거나 고치기 전에 반드시 먼저 계획을 설명하고 내 승인을 받아라.**
   만들 파일, 각 파일의 역할, 설계상의 선택과 그 이유를 말로 설명한 뒤 진행 여부를 물어라.
   조사·파일 읽기·컴파일 확인·문서 갱신은 승인 없이 해도 된다. 코드 파일에 손대는 순간부터 승인이 필요하다.
   이유: 나는 배우면서 진행하는 학기 프로젝트다. 코드가 먼저 완성돼 나오면 왜 그렇게 만들었는지 따라갈 기회가 사라진다.

2. **한 번에 많이 만들지 마라.** 파일이 5~6개를 넘어갈 것 같으면 단계를 쪼개서 제안해라.
   각 단계는 그 자체로 눈으로 확인 가능해야 한다.

3. **작업이 끝나면 두 곳에 기록해라.**
   - `README.md` (프로젝트 루트): 명세 상태, 사용법, 변경 이력
   - Notion 워크스페이스 "민규 주의 Notion" → `끄아아악 / SafeWatch / 3,4주차` 페이지
     어조는 같은 폴더의 `1,2주차` 페이지를 따라라. 1인칭 회고체로, 무엇을 했는지보다
     **왜 그렇게 판단했는지**와 **막힌 지점의 원인**을 적고, 새로 나온 용어는 표로 정리한다.
   - Notion MCP 접근이 없으면 README만 갱신하고 나한테 알려라.

4. **검증은 8081 포트에 따로 띄워서 해라.** 내가 8080에 서버를 띄워 쓰고 있을 수 있다.
   `./gradlew bootRun --args='--server.port=8081'` 로 띄우고, 확인이 끝나면 반드시 내려라.
   테스트로 만든 DB 데이터(계정, 이벤트)도 지워서 시드 상태로 되돌려라.

5. **설명은 한국어로.** 용어를 처음 쓸 때는 풀어서 설명해라.

## 1. 프로젝트

SafeWatch — 차량 Edge AI(라즈베리파이)가 감지한 음주·난폭운전 정보를 사용자 모바일 앱까지 전달하는 서비스.
2026 파란학기 프로젝트. 내 담당은 **앱 + 백엔드**이고, AI 파트는 다른 팀원(설만수)이 맡는다.

데이터 흐름:

```
[라즈베리파이 Edge AI]  →  [백엔드 서버]  →  [Flutter 앱]  →  [폰 로컬 DB]
   위험운전 감지            PostgreSQL 저장     목록·상세 확인    offline-first 저장
   영상·위치·점수           FCM 푸시(미구현)    신고/무시 처리    통신 끊겨도 열람
```

앱이 폰에 한 번 더 저장하는 이유는 차량에서 통신이 자주 끊기기 때문이다.

## 2. 환경

- 프로젝트 루트: `/Users/alsrb1125/Desktop/SafeWatch/SafeWatch`
- 백엔드: Spring Boot 4.1.1, Java 21, Gradle (`backend/`)
- DB: PostgreSQL 16, Docker 컨테이너 `safewatch-db` (`docker/docker-compose.yml`)
- 앱: Flutter (미착수, 별도 예정) / 지도: 카카오맵 / 푸시: FCM
- **아직 git 저장소가 아니다.** `git init` 안 했다.

실행:

```bash
docker compose -f docker/docker-compose.yml up -d   # DB
cd backend && ./gradlew bootRun                     # 서버 :8080
open frontend/index.html                            # 확인용 화면
curl http://localhost:8080/api/events               # 시드 이벤트 3건
```

DB 접속: host localhost, port 5432, db/user/pw 모두 `safewatch`

### 환경에서 자주 막히는 것 (실제로 여러 번 겪음)

- **실행이 안 되면 코드보다 포트를 먼저 의심해라.**
  `Port 8080 was already in use` 가 `BUILD FAILED` 와 함께 뜨면 이미 서버가 떠 있는 것이다. 코드 문제가 아니다.
  ```bash
  lsof -nP -iTCP:8080 -sTCP:LISTEN
  kill $(lsof -tnP -iTCP:8080 -sTCP:LISTEN)
  ```
- 5432에 brew PostgreSQL이 붙어 있으면 서버가 Docker DB가 아닌 brew DB에 연결돼
  `FATAL: role "safewatch" does not exist` 가 난다. `brew services stop postgresql@18`.
  brew는 로그인 때 자동으로 다시 켜지므로 재발할 수 있다.
- **`bootRun` 은 실행 시점의 빌드를 쓴다.** 파일을 저장했다고 떠 있는 서버에 반영되지 않는다.
  새 컨트롤러가 404면 재시작을 먼저 의심해라. (실제로 이것 때문에 한참 헤맸다)
- 내 터미널은 sdkman으로 Java 21을 쓰므로 `JAVA_HOME` 을 따로 지정할 필요 없다.
  다만 도구가 비대화형 셸로 실행하면 Java 11이 잡힐 수 있다. 그때는
  `export JAVA_HOME=/Users/alsrb1125/.sdkman/candidates/java/21.0.2-tem`

## 3. DB 스키마 (5개 테이블)

`docker/initdb/01-schema.sql` 에 정의. `backend/schema.sql` 과 **내용이 완전히 같은 사본**이다.

```
users 1──N devices 1──N events 1──N reports
users 1──N reports
users 1──N user_fcm_tokens
```

| 테이블 | 주요 컬럼 |
|---|---|
| users | id(UUID PK), email(unique), password_hash, display_name, created_at |
| devices | id(UUID PK), name, owner_user_id(FK→users), registered_at |
| events | id(UUID PK), device_id(FK→devices), detected_at, received_at, risk_type, risk_score, latitude, longitude, plate_number, video_url, thumb_url, status, created_at |
| reports | id(UUID PK), event_id(FK→events), user_id(FK→users), reported_at, memo |
| user_fcm_tokens | id(UUID PK), user_id(FK→users), token(unique), platform, created_at, updated_at |

CHECK 제약: `risk_type` 은 `drunk_suspect / lane_departure / sudden_accel / swerving`,
`risk_score` 는 0~100, `status` 는 `new / reviewed / reported` (기본 `new`),
`platform` 은 `android / ios`.

시드 데이터: users 2, devices 2, events 3, reports 1.
장치 id는 `aaaaaaaa-0000-0000-0000-00000000000{1,2}`.
**시드 계정의 password_hash 는 `HASHED_pw_1` 같은 가짜 문자열이라 로그인이 안 된다(401). 정상이다.**

### 스키마를 바꿔야 할 때

`docker/initdb` 의 SQL은 **컨테이너를 처음 만들 때만** 실행된다. 이미 돌고 있는 DB에는 파일만 고쳐서는 반영되지 않는다. 두 곳을 다 손봐야 한다.

1. 돌고 있는 DB에 직접 적용: `docker exec -i safewatch-db psql -U safewatch -d safewatch <<'SQL' ... SQL`
2. `backend/schema.sql` 수정 후 `cp backend/schema.sql docker/initdb/01-schema.sql`
3. 일치 확인: 임시 DB를 만들어 파일만으로 스키마를 세운 뒤 실제 DB와 테이블·컬럼 목록 비교

## 4. 지금까지 만든 것

자바 파일 18개. 패키지는 `com.safewatch.server` 아래 `auth`, `event`, `config`, `common`.

### API 명세 12개 중 6개 구현

| 분류 | 기능 | Method | URI | 상태 |
|---|---|---|---|---|
| 인증 | 회원가입 | POST | /api/auth/signup | **구현** |
| 인증 | 로그인 | POST | /api/auth/login | **구현** |
| 인증 | 내 정보 조회 | GET | /api/auth/me | 미구현 |
| 인증 | FCM 토큰 등록 | POST | /api/auth/fcm-token | 미구현 |
| 이벤트 | 위험 이벤트 업로드 | POST | /api/events | **구현** |
| 이벤트 | 목록·증분 조회 | GET | /api/events?since= | **구현** |
| 이벤트 | 상세 조회 | GET | /api/events/{id} | **구현** |
| 이벤트 | 상태 변경(신고/무시) | PATCH | /api/events/{id} | **구현** |
| 장치 | 내 장치 목록 | GET | /api/devices | 미구현 |
| 장치 | 장치 등록 | POST | /api/devices | 미구현 |
| 신고 | 신고 등록 | POST | /api/reports | 미구현 |
| 신고 | 내 신고 목록 | GET | /api/reports | 미구현 |

### 확인용 웹 화면

`frontend/index.html` — 외부 라이브러리 없는 파일 하나. Flutter 앱 전에 동작을 눈으로 보려고 만들었다.
이벤트 목록(5초 자동 갱신) + 신고/무시 버튼, 장치 시뮬레이터(라즈베리파이 흉내), 회원가입, 로그인(토큰 내용 풀어서 표시), 응답 기록.
실패 시연용 선택지도 넣어뒀다(등록 안 된 장치, 잘못된 위험 종류, 틀린 비밀번호, 없는 계정).

## 5. 코드 규칙 (반드시 따를 것)

**모든 엔드포인트에 `/api` 접두사가 자동으로 붙는다.**
`config/WebConfig.java` 가 `@RestController` 전체에 붙인다. 컨트롤러에는 자원 경로만 써라.

```java
@RestController
@RequestMapping("/events")   // 실제 주소는 /api/events
```

컨트롤러에 `/api` 를 직접 쓰지 마라. 예전에 이벤트만 `/api/events` 이고 나머지는 접두사가 없어 규칙이 갈라진 적이 있다.

**엔티티를 그대로 주고받지 않는다.** DB용 엔티티와 요청·응답 DTO를 나눈다.

| 클래스 | 역할 |
|---|---|
| `Event`, `User` | 테이블 매핑 (DB 전용) |
| `EventCreateRequest`, `EventStatusUpdateRequest`, `SignupRequest`, `LoginRequest` | 요청 |
| `EventResponse`, `UserResponse`, `LoginResponse` | 응답 |

이유: 엔티티를 그대로 응답에 쓰면 테이블에 있는 값이 전부 나간다. `UserResponse` 에 `passwordHash` 가 없는 게 핵심이다.

**엔티티에 세터를 열지 않는다.** 바꿔도 되는 것만 메서드로 연다.
`Event.changeStatus()` 하나만 있다. 위험도·위치·시각은 장치가 보낸 사실이라 고쳐지면 안 된다.

**검증은 DTO에서 한다.** DB의 CHECK 제약과 같은 값으로 맞춰 둔다. 그래야 500 대신 400이 나간다.

**오류는 `common/ApiExceptionHandler` 가 변환한다.**
검증 실패 → 400(필드별 사유), DB 제약 위반 → 400, `ResponseStatusException` → 해당 코드 + 사유.

## 6. 설계 판단과 이유 (바꾸기 전에 읽을 것)

대부분 **차량이라는 환경**에서 근거가 나왔다. 통신이 끊기고, 장치 시계가 틀리고, 데이터가 몰려 올라온다.

- **id를 장치가 만든다(UUID)** — 자동증가 번호는 통신이 끊겼다 몰아서 올라올 때 꼬인다. 중복을 걸러내는 기준도 이 값이다.
- **시각이 둘(`detected_at` / `received_at`)** — 통신 지연으로 벌어진다. 장치 시계는 틀릴 수 있어 목록 정렬은 서버 시각을 쓴다.
- **같은 id 재전송은 409가 아니라 200** — 충돌로 응답하면 장치가 실패로 보고 무한히 재시도한다. 기존 것을 그대로 돌려준다.
- **영상은 URL만 저장** — 파일은 용량이 크다.
- **`since` 증분 조회** — 앱의 offline-first 설계와 맞물린다. 전체를 다시 받지 않는다.
- **FCM 토큰은 users 컬럼이 아니라 1:N 별도 테이블** — 폰 두 대를 쓰거나 기기를 바꿀 수 있다. 컬럼 하나면 새 폰 로그인 시 이전 폰이 알림을 못 받는다. 안전 서비스에서 알림 누락은 단순한 불편이 아니다.
- **`ddl-auto: none`** — 스키마는 SQL로 직접 만들었으므로 JPA가 건드리지 않게 한다.
- **Spring Security 채택** — 직접 구현 / 세션 방식 / Firebase 위임과 비교 후 결정. 인증은 코드가 돌아가도 틀린 게 안 보이는 영역이라 검증된 것을 쓰기로 했다. 세션은 여러 기기에 안 맞고, Firebase는 설계해둔 계정 구조가 무의미해진다.
- **로그인 실패 문구를 구분하지 않는다** — "없는 이메일"과 "비밀번호 틀림"을 나눠 주면 이메일만 바꿔가며 계정 존재를 알아낼 수 있다. 둘 다 401에 같은 문구.
- **계정이 없어도 해시 비교를 한 번 돌린다** — 없을 때 곧장 실패하면 응답이 빨라져서 시간 차이로 계정 존재가 드러난다. `AuthController` 의 `dummyHash` 가 그 용도다.

## 7. Spring Security 설정에서 반드시 주의할 것

`SecurityConfig` 는 넣는 순간 **모든 요청을 막는 것이 기본값**이다. 열 곳을 직접 적어야 한다.

현재 열어둔 것:
- `/api/auth/**` — 토큰 받기 전에 부르는 곳
- `/api/events/**` — 장치 인증 방식이 미정이고 앱 로그인도 아직 안 붙어서 임시로 열어둠. **로그인이 붙으면 좁혀야 한다.**
- `DispatcherType.ERROR` — 아래 설명 참고
- CORS — `frontend/index.html` 이 서버 밖에서 열리므로 필요. 지금은 localhost와 file 열기(`null` origin)만 허용. **배포 시 실제 도메인으로 좁혀야 한다.**

**이미 겪은 버그 두 개 (같은 실수 반복하지 마라):**

1. **404·409가 전부 403으로 나갔다.** 404 같은 응답은 컨트롤러가 직접 만들지 않는다. 예외를 던지면 톰캣이 `/error` 로 내부 요청을 한 번 더 보내 본문을 만드는데, 그게 `anyRequest().authenticated()` 에 막혔다. 해결: `.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`
2. **오류 사유 문구가 안 실렸다.** Spring Boot가 기본적으로 오류 메시지를 감춘다(DB 구조나 내부 경로 노출 방지). `ApiExceptionHandler` 에서 `ResponseStatusException` 을 직접 받아 우리가 적은 문구만 골라 내려주게 했다.

## 8. 보안상 지금 위험한 상태

- **`backend/src/main/resources/application.yml` 에 JWT 개발용 비밀키가 평문으로 있다.**
  `JWT_SECRET` 환경변수가 있으면 그걸 쓰고 없으면 기본값을 쓰는 구조다.
  **`git init` 하고 깃허브에 올리기 전에 반드시 기본값을 지우고 `${JWT_SECRET}` 만 남겨야 한다.**
  이 값을 아는 쪽은 아무 토큰이나 만들어낼 수 있다.
- DB 비밀번호도 같은 파일에 평문(`safewatch`)이다. 로컬 개발용이라 지금은 괜찮지만 배포 시 분리해야 한다.
- `중요자료/safewatch_key.pem` (AWS EC2 키)가 프로젝트 상위 폴더에 있다. 저장소에 포함되지 않게 주의.

## 9. 문서 상태 (먼저 할 일)

**로그인(1-B)과 CORS 작업이 코드에는 있지만 문서에 기록되지 않았다.**
`README.md` 의 명세표에 로그인이 아직 "미구현"으로 되어 있고, CORS는 언급이 없다.
노션 3,4주차 페이지도 로그인 전까지만 기록돼 있다.

첫 작업으로 이 두 문서를 현재 코드 상태에 맞게 갱신해라. 내용은 다음과 같다:
- 로그인 구현(JWT 발급, jjwt 0.12.6, 만료 120분, 응답에 토큰 + 사용자 정보)
- 실패 문구 통일과 타이밍 공격 방어
- CORS 설정 추가와 그 이유(frontend 폴더가 서버 밖이라서)
- `frontend/index.html` 확인용 화면 추가

## 10. 다음 할 일 (순서)

1. **위 문서 갱신** (로그인·CORS 기록)
2. **인증 2단계** — 토큰을 검사하는 필터, `GET /api/auth/me`, FCM 토큰 등록(`user_fcm_tokens` 사용)
3. **SecurityConfig 범위 좁히기** — 로그인이 붙었으니 이벤트 API를 인증 필요로 변경
4. **구글 로그인 추가** — 아주메일(`@ajou.ac.kr`)은 구글이 운영하므로(MX가 전부 Google) 구글 로그인 하나로 학교 계정까지 커버된다. 단 **앱에서의 구글 로그인은 웹과 흐름이 다르다.** 앱이 구글에서 ID 토큰을 받아 서버로 보내고 서버가 검증하는 방식이어야 한다. `users.password_hash` 가 NOT NULL이라 소셜 가입을 받으려면 스키마 변경 필요(비워둘 수 있게 + 가입 경로 기록).
5. **장치·신고 API** — 신고 버튼은 지금 `events.status` 만 바꾼다. `reports` 테이블은 만들어뒀지만 자바 코드에서 쓰는 곳이 없다. 누가 신고했는지 남기려면 로그인 위에 얹어야 한다.
6. **FCM 푸시** — 이벤트 수신 시 해당 장치 소유자에게 발송. 핵심 루프에서 유일하게 사용자에게 안 닿는 부분.
7. **Flutter 앱** — 로그인 → 목록(offline-first, sqflite) → 상세(영상 + 카카오맵) → 신고
8. **AWS 배포** — EC2 + RDS

## 11. 외부 의존 (내가 통제 못 하는 것)

- **AI 파트(설만수)의 모델이 아직 완성 전이다.** 라즈베리파이가 보내는 실제 이벤트 JSON 형식이 미확정.
  완성되면 내가 형식을 그대로 전달할 테니 그에 맞춰 수정해라.
  바뀔 가능성이 큰 것: `risk_type` 의 가짓수, `risk_score` 범위, 번호판·영상 전달 방식.
  지금은 우리 설계안 기준이고, 고칠 자리를 `EventCreateRequest` 한 파일로 모아뒀다.
- **영상 전달 방식 미확정** — 장치가 S3 등에 올리고 URL만 주는지, 서버로 업로드하는지.
- **개인정보 처리 기준 미정** — 타인 차량의 번호판·위치·영상을 수집해 저장한다. 보관 기간과 신고 외 사용 제한을 정해야 한다.

---

먼저 프로젝트를 훑어보고, 위 9번의 문서 갱신부터 시작할지 물어봐라.
