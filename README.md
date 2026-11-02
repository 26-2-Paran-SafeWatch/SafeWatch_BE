# SafeWatch — 백엔드

차량 Edge AI(라즈베리파이)가 감지한 위험운전 정보를 사용자 모바일 앱까지 전달하는 서비스.
이 저장소는 그중 **백엔드 서버와 DB**를 담당한다. (담당: 주민규 — 앱 + 백엔드)

## 데이터 흐름

```
[라즈베리파이 Edge AI]      [백엔드 서버]         [React Native 앱]   [폰 로컬 DB]
   위험운전 감지    ──▶   POST /api/events  ──▶   목록/상세 확인  ──▶   offline-first 저장
   영상·위치·점수          PostgreSQL 저장          신고 처리           통신 끊겨도 열람 가능
                          FCM 푸시 발송(예정)
```

앱이 폰에 한 번 더 저장하는 이유는 차량 환경에서 통신이 자주 끊기기 때문이다.
마지막으로 받은 위험 목록을 오프라인에서도 볼 수 있고, 매번 전체를 다시 받지 않아도 된다.

## 폴더 구조

```
SafeWatch/
├── backend/                    # Spring Boot 서버
│   ├── src/main/java/com/safewatch/server/
│   │   ├── ServerApplication.java
│   │   ├── config/WebConfig.java      # 전역 /api 접두사
│   │   ├── config/SecurityConfig.java # Spring Security
│   │   ├── common/ApiExceptionHandler.java   # 오류 응답 변환
│   │   ├── auth/                      # User, UserRepository,
│   │   │                              # SignupRequest, UserResponse,
│   │   │                              # AuthController
│   │   └── event/                     # Event, EventCreateRequest,
│   │                                  # EventStatusUpdateRequest,
│   │                                  # EventResponse,
│   │                                  # EventRepository, EventController
│   ├── src/main/resources/application.yml
│   ├── schema.sql / seed.sql / queries.sql   # docker/initdb 와 동일 내용
│   └── build.gradle
├── device/                     # 라즈베리파이에 올릴 코드
│   └── send_events.py          # 위험 이벤트 전송 (표준 라이브러리만 사용)
├── frontend/
│   └── index.html              # 개발 확인용 웹 콘솔
└── docker/
    ├── docker-compose.yml      # PostgreSQL 16
    └── initdb/                 # 컨테이너 최초 생성 시 자동 실행
        ├── 01-schema.sql
        └── 02-seed.sql
```

## 기술 스택

| 구분 | 선택 |
|---|---|
| 백엔드 | Spring Boot 4.1.1, Java 21, Gradle |
| DB | PostgreSQL 16 (로컬 Docker, 배포 시 AWS RDS) |
| 앱 | React Native (별도 저장소, 미착수) |
| 지도 | 카카오맵 (앱 전용, 서버 API 불필요) |
| 푸시 | FCM (미구현) |
| 인증 | Spring Security + JWT (가입 완료, 로그인 미구현) |

## 실행 방법

### 1. DB 실행

```bash
docker compose -f docker/docker-compose.yml up -d
```

### 2. 비밀값 파일 만들기 (저장소를 새로 받았다면 한 번만)

JWT 비밀키는 저장소에 올라가지 않는다. 이 값을 아는 쪽은 아무 토큰이나 만들어 누구인 척할 수 있기 때문이다. 직접 만들어야 한다.

```bash
mkdir -p backend/config
cat > backend/config/application.yml <<EOF
safewatch:
  jwt:
    secret: $(openssl rand -base64 48)
EOF
```

스프링이 실행 폴더의 `config/` 를 자동으로 읽고 `src/main/resources` 의 같은 설정보다 우선한다. 그래서 실행할 때 따로 붙일 것이 없다.

이 파일이 없으면 서버가 뜨지 않는다. 배포 환경에서는 이 파일 대신 `JWT_SECRET` 환경변수를 쓴다.

### 3. 서버 실행

```bash
cd backend
./gradlew bootRun          # http://localhost:8080
```

### 4. 확인

```bash
curl http://localhost:8080/api/events     # 시드 이벤트 3건이 JSON 으로 반환되면 성공
```

### 실행 전 포트 확인 (중요)

이 프로젝트에서 가장 자주 막힌 원인이 포트 충돌이다. 실행이 안 되면 코드부터 의심하지 말고 포트를 먼저 본다.

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN     # 서버 포트
lsof -nP -iTCP:5432 -sTCP:LISTEN     # DB 포트
```

- **8080이 이미 잡혀 있으면** 서버가 이미 떠 있는 것이다. 또 실행하면 `Port 8080 was already in use` 와 함께 `BUILD FAILED` 가 뜬다. 빌드 실패처럼 보이지만 코드 문제가 아니다.
  - 기존 것 내리기: `kill $(lsof -tnP -iTCP:8080 -sTCP:LISTEN)`
  - 또는 다른 포트로 띄우기: `./gradlew bootRun --args='--server.port=8081'`
- **5432에 brew PostgreSQL이 붙어 있으면** 서버가 Docker DB가 아닌 brew DB에 연결되어 `FATAL: role "safewatch" does not exist` 가 난다. brew 쪽을 끈다.
  - `brew services stop postgresql@18`
  - brew는 로그인할 때 자동으로 다시 켜지므로 다음 날 재발할 수 있다.

### 코드를 고쳤는데 반영이 안 될 때

`bootRun`은 실행 시점의 빌드를 사용한다. 파일만 저장하고 서버를 재시작하지 않으면 **새로 만든 컨트롤러가 404** 로 나온다. 저장 후에는 반드시 서버를 껐다 켠다.

## API 규칙

**모든 엔드포인트는 `/api` 접두사를 쓴다.** 접두사는 `config/WebConfig.java` 가 `@RestController` 전체에 자동으로 붙인다.

```java
@RestController
@RequestMapping("/events")     // 컨트롤러에는 자원 경로만 쓴다
// 실제 주소 → /api/events
```

컨트롤러에 `/api` 를 직접 쓰지 않는다. 새 컨트롤러를 추가할 때 접두사를 빠뜨릴 수 없으므로 규칙이 갈라지지 않는다.

## API 명세 (12개)

| 분류 | 기능 | Method | URI | 상태 |
|---|---|---|---|---|
| 인증 | 회원가입 | POST | `/api/auth/signup` | **구현됨** |
| 인증 | 로그인 | POST | `/api/auth/login` | 미구현 |
| 인증 | 내 정보 조회 | GET | `/api/auth/me` | 미구현 |
| 인증 | FCM 토큰 등록 | POST | `/api/auth/fcm-token` | 미구현 |
| 이벤트 | 위험 이벤트 업로드 | POST | `/api/events` | **구현됨** |
| 이벤트 | 위험 이벤트 목록 | GET | `/api/events?since=` | **구현됨** |
| 이벤트 | 이벤트 상세 | GET | `/api/events/{id}` | **구현됨** |
| 이벤트 | 이벤트 상태 변경 | PATCH | `/api/events/{id}` | **구현됨** |
| 장치 | 내 장치 목록 | GET | `/api/devices` | 미구현 |
| 장치 | 장치 등록 | POST | `/api/devices` | 미구현 |
| 신고 | 신고 등록 | POST | `/api/reports` | 미구현 |
| 신고 | 내 신고 목록 | GET | `/api/reports` | 미구현 |

로그인 시 JWT를 발급하고 이후 요청은 `Authorization: Bearer <token>` 헤더를 쓴다(예정).
이벤트 업로드는 앱이 아니라 **라즈베리파이 장치**가 호출한다.

### 스키마를 바꿔야 할 때

`docker/initdb` 의 SQL 은 **컨테이너를 처음 만들 때만** 실행된다. 이미 돌고 있는 DB 에는 파일만 고쳐서는 반영되지 않는다. 두 곳을 모두 손봐야 한다.

```bash
# 1) 돌고 있는 DB 에 직접 적용
docker exec -i safewatch-db psql -U safewatch -d safewatch <<'SQL'
ALTER TABLE ... ;
SQL

# 2) 파일도 같이 수정 (다음에 새로 만들 때 반영되도록)
#    backend/schema.sql 을 고치고 docker/initdb/01-schema.sql 로 복사
cp backend/schema.sql docker/initdb/01-schema.sql
```

파일과 실제 DB 가 같은지 확인하려면 임시 DB 에 파일만으로 스키마를 세워 비교한다.

```bash
docker exec safewatch-db psql -U safewatch -d postgres -c "CREATE DATABASE schemacheck;"
docker exec -i safewatch-db psql -U safewatch -d schemacheck < backend/schema.sql
# 두 DB 의 테이블/컬럼 목록 비교 후
docker exec safewatch-db psql -U safewatch -d postgres -c "DROP DATABASE schemacheck;"
```

## 엔티티와 DTO

`Event` 엔티티는 **DB 에 어떻게 저장할지**만 정한다. 밖으로 나가고 들어오는 모양은 별도 객체가 정한다.

| 클래스 | 역할 |
|---|---|
| `Event` | events 테이블 매핑 (DB 전용) |
| `EventCreateRequest` | 장치가 보내는 등록 요청 |
| `EventStatusUpdateRequest` | 앱이 보내는 상태 변경 요청 |
| `EventResponse` | 앱에 돌려주는 응답 |

**엔티티를 그대로 주고받지 않는 이유.** 한 클래스가 저장과 노출을 겸하면 DB 에 있는 값이 전부 그대로 밖으로 나간다. 예를 들어 곧 만들 `User` 엔티티에는 `password_hash` 가 있어야 로그인 대조를 할 수 있는데, 엔티티를 그대로 응답에 쓰면 내 정보 조회 한 번에 비밀번호 해시가 함께 나간다.

받는 쪽도 마찬가지다. 등록 요청을 엔티티로 직접 받으면 장치가 `status` 를 `reported` 로 넣어 보낼 수 있다. `EventCreateRequest` 에는 그 필드가 아예 없으므로 서버가 정하는 값을 장치가 건드릴 수 없다.

## 이벤트 API 사용법

### 등록 — 라즈베리파이가 호출

본문은 **AI 파트 이벤트 JSON 그대로**다(SafeWatch_AI `docs/event-schema.md` v0.7, 5.1 확정안). AI 의 `src/event/builder.py` 나 `scripts/generate_dummy_event.py` 가 만든 JSON 을 변환 없이 보내면 된다. 그래서 이 요청만 필드 이름이 snake_case 다. 2026-11-02 에 이 형식으로 바꿨다.

```bash
python3 ai/scripts/generate_dummy_event.py --count 3 --output /tmp/samples   # AI 더미 생성기
curl -X POST http://localhost:8080/api/events \
  -H 'Content-Type: application/json' --data @/tmp/samples/evt_....json
```

```json
{
  "event_id": "evt_20261102_143022_001",
  "device_id": "safewatch-dummy-001",
  "timestamp": "2026-11-02T14:30:22.451+09:00",
  "risk": { "score": 78, "level": "high", "types": ["lane_departure", "weaving"] },
  "indicators": { "observation_window_sec": 10.0, "lane_departure_duration_sec": 2.8, "direction_changes_count": 4 },
  "target_vehicle": { "track_id": 17, "tracked_duration_sec": 12.4, "avg_detection_confidence": 0.87 },
  "location": { "latitude": 37.32, "longitude": 126.83, "speed_kmh": 62.5, "heading": 271.3 },
  "clip": { "filename": "evt_20261102_143022_001.mp4", "duration_sec": 15, "resolution": "1280x720", "blurred": true },
  "meta": { "model_version": "yolov8n-safewatch-v0.3", "rule_version": "risk-v1.2" }
}
```

| 필드 | 필수 | 규칙 |
|---|---|---|
| `event_id` | ✓ | 장치 안에서 고유한 번호 |
| `device_id` | ✓ | **등록된 장치 이름** (`devices.code`). 지금은 `safewatch-dev-001`, `safewatch-dummy-001` |
| `timestamp` | ✓ | ISO 8601, 타임존 포함 |
| `risk.score` | ✓ | 0~100 |
| `risk.level` | ✓ | `low` / `medium` / `high` |
| `risk.types` | ✓ | 1개 이상. `lane_departure` / `weaving` / `swerving` / `drifting` / `speed_irregular` |
| `indicators`, `target_vehicle`, `clip`, `meta` | | 받은 그대로 저장 (키를 고정하지 않음) |
| `location` | | GPS 를 못 잡으면 통째로 빼도 된다 |

| 상황 | 응답 |
|---|---|
| 정상 등록 | 201, `Location` 헤더에 상세 주소 |
| 같은 장치가 같은 `event_id` 재전송 | 200, 기존 이벤트 반환 (중복 생성 안 됨) |
| 규칙 위반 (예: 모르는 `risk.types`) | 400, `errors` 에 어느 필드가 왜 틀렸는지 |
| 등록되지 않은 `device_id` | 400, "등록되지 않은 장치입니다" |

**같은 id 를 다시 보내면 왜 409 가 아니라 200 인가.** 차량은 통신이 자주 끊긴다. 장치가 이벤트를 보냈는데 응답을 못 받으면 같은 것을 다시 보낸다. 이때 409(충돌)를 주면 장치는 실패로 보고 계속 재시도한다. 중복 전송이 안전하도록 이미 받은 이벤트면 그대로 200 을 돌려준다.

**서버가 id 를 따로 매기는 이유.** AI 의 `event_id` 는 `evt_날짜_시각_순번` 이라 **차 두 대가 같은 초에 감지하면 똑같아진다.** 이걸 그대로 고유 키로 쓰면 나중 것이 중복으로 처리돼 사라진다. 그래서 서버는 자기 UUID(`id`)를 쓰고, AI 번호는 `eventKey` 로 따로 두어 **(장치, eventKey) 짝**으로 중복을 판별한다.

**응답(앱용)은 camelCase** 다: `riskScore`, `riskLevel`, `riskTypes`, `indicators`, `speedKmh`, `clip`, `videoUrl` 등. `target_vehicle`, `meta` 는 저장만 하고 앱에는 내보내지 않는다(디버깅용).

**아직 없는 것: 영상 파일.** 전송 방식(AI 문서 1장 A/B/C안)이 정해지면 붙인다. 그 전까지 `videoUrl` 은 비어 있고, 앱은 클립 정보가 있으면 "영상 업로드 대기 중"을 보여준다.

### 목록 / 상세

```bash
curl http://localhost:8080/api/events                              # 전체, 수신 최신순
curl "http://localhost:8080/api/events?since=2026-09-10T00:00:00%2B09:00"   # 증분
curl http://localhost:8080/api/events/{id}                         # 상세, 없으면 404
```

### 상태 변경 — 앱에서 신고/무시

```bash
curl -X PATCH http://localhost:8080/api/events/{id} \
  -H 'Content-Type: application/json' \
  -d '{"status": "reported"}'
```

| status | 뜻 |
|---|---|
| `new` | 아직 확인 안 함 (등록 시 기본값) |
| `reviewed` | 확인하고 넘김 (무시) |
| `reported` | 신고함 |

바꿀 수 있는 값은 `status` 하나뿐이다. 위험도·위치·시각은 장치가 보낸 사실이므로 나중에 고칠 수 없다. 엔티티에 세터를 열지 않고 `changeStatus()` 메서드만 둔 이유다.

허용되지 않는 값이면 400, 없는 이벤트면 404.

### 영상 보관·파기 (2026-10-26)

영상은 신고용 증거라서, 목적이 끝나면 서버가 지운다. 앱이 꺼져 있어도 규칙이 지켜지도록 서버가 집행한다.

| 경우 | 처리 |
|---|---|
| `reviewed`(무시)로 바뀜 | 그 요청 안에서 즉시 파기 |
| 서버가 받은 지 2주 지남 | 신고 여부와 관계없이 자동 파기 (`VideoRetentionJob`, 매시 정각 + 서버 기동 시) |

- 파기 = `video_url`·`thumb_url` 을 비우고 `video_deleted_at` 에 시각을 남긴다. 응답에는 `videoExpiresAt`(자동 파기 예정)과 `videoDeletedAt` 이 함께 나간다.
- 기준 시각은 `detected_at` 이 아니라 `received_at` 이다. 장치 시계가 틀리면 받자마자 파기되는 사고를 막기 위해서다.
- 보관 기간은 `Event.VIDEO_RETENTION` 한 곳에만 있다. 이용약관에 적을 값이므로 바꿀 때는 약관도 같이 고친다.
- **한계:** DB 의 주소만 지운다. 실제 파일은 저장소(미정)에 남으므로 저장소가 정해지면 `Event.purgeVideo()` 에 파일 삭제를 붙여야 한다.
- 이유(법적 문제·용량)는 노션 9,10주차 페이지에 정리.

### 참고

`since` 는 앱이 마지막으로 받은 시각 이후 것만 가져가는 증분 동기화용이다. 매번 전체를 다시 받지 않아도 되므로 offline-first 설계와 맞물린다. URL 에 넣을 때 `+` 는 `%2B` 로 써야 한다.


## 인증

### 회원가입

```bash
curl -X POST http://localhost:8080/api/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"mingyu@ajou.ac.kr","password":"safewatch1234","displayName":"주민규"}'
```

비밀번호는 8자 이상 64자 이하. 응답에는 `id`, `email`, `displayName`, `createdAt` 만 담기고 **비밀번호 해시는 나가지 않는다**(`UserResponse`).

| 상황 | 응답 |
|---|---|
| 정상 가입 | 201 |
| 이미 가입된 이메일 | 409 |
| 이메일 형식 오류, 짧은 비밀번호 | 400, 필드별 사유 |

비밀번호는 BCrypt 로 해시해서 저장한다. BCrypt 는 일부러 느린 해시라, 비밀번호를 훔친 쪽이 후보를 대입해보는 속도도 같이 느려진다. 소금값이 매번 달라 같은 비밀번호라도 저장된 해시는 서로 다르다.

### Spring Security 설정에서 주의할 것

`SecurityConfig` 는 넣는 순간 **모든 요청을 막는 것이 기본값**이다. 열 곳을 직접 적어야 한다.

현재 열어둔 곳은 `/api/auth/**` 와 `/api/events/**` 두 갈래다. 이벤트 API 를 연 이유는 등록을 부르는 라즈베리파이의 장치 인증 방식이 아직 정해지지 않았고, 조회를 부르는 앱의 로그인 기능도 아직 없기 때문이다. 둘 다 붙으면 이 범위를 좁힌다.

그리고 **오류 응답 경로도 함께 열어야 한다.**

```java
.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
```

404 나 409 같은 응답은 컨트롤러가 직접 만들지 않는다. 예외를 던지면 톰캣이 오류 본문을 만들려고 내부 요청을 한 번 더 보내는데, 이 줄이 없으면 그 내부 요청이 막혀 **원래 만들려던 404 대신 본문 없는 403** 이 나간다.

### 오류 사유 문구

Spring Boot 는 기본적으로 오류 메시지를 감춘다. 메시지에 DB 구조나 내부 경로가 딸려 나가면 공격에 힌트가 되기 때문이다. 그래서 전체를 여는 대신 `ApiExceptionHandler` 가 `ResponseStatusException` 을 직접 받아, 우리가 적은 문구만 골라서 내려준다.

```json
{"title":"Conflict","status":409,"detail":"이미 가입된 이메일입니다"}
```


## 장치 연동

`device/send_events.py` 는 라즈베리파이가 서버로 위험 이벤트를 올리는 코드다.
표준 라이브러리만 쓰므로 라즈베리파이에 따로 설치할 것이 없다.

```bash
python3 device/send_events.py --server http://<서버주소>:8080
python3 device/send_events.py --server http://<서버주소>:8080 --interval 3 --count 10
```

### 두 종류의 실패를 다르게 처리한다

| 상황 | 처리 | 이유 |
|---|---|---|
| 서버에 닿지 못함 | 쌓아두고 다음에 재전송 | 터널·지하주차장. 우리 잘못이 아니므로 버리면 안 된다 |
| 서버가 거절(4xx) | 버림 | 보낸 값이 잘못된 것이라 다시 보내도 결과가 같다 |

구분하지 않으면 잘못된 요청을 영원히 재시도하면서 큐가 계속 불어난다.

같은 이벤트를 다시 보내도 서버가 `200` 으로 받아주므로(`POST /api/events` 참고) 중복이 쌓이지 않는다.

### 실제 라즈베리파이에 올릴 때 바꿔야 하는 것

1. **서버 주소** — 실행 인자로 넘긴다. 같은 네트워크에 있어야 한다.
2. **장치 id** — `devices` 테이블에 등록된 값이어야 한다. 없는 값은 `400` 으로 거절된다.
3. **`make_event()` 내부** — 지금은 무작위로 만든다. AI 파트의 실제 감지 결과로 교체할 자리다.

### 아직 없는 것 (실제 차량 운용 전 필요)

- **자동 실행** — 시동을 걸면 알아서 돌아야 한다. systemd 서비스 등록 필요.
- **쌓아둔 것의 파일 저장** — 지금은 메모리에만 있어 전원이 꺼지면 사라진다. 차는 시동을 끄면 장치도 꺼지므로 터널에서 쌓아둔 것을 주차하며 잃는다.
- **시계 보정** — 라즈베리파이는 시계용 배터리가 없어 전원이 꺼지면 시각을 잊는다. 부팅 후 인터넷에 붙기 전까지 `detected_at` 이 틀릴 수 있다. 서버가 `received_at` 을 따로 적는 이유가 이것이다.

## DB 스키마

`docker/initdb/01-schema.sql` 에 정의. 4개 테이블.

```
users 1──N devices 1──N events 1──N reports
users 1──N reports
users 1──N user_fcm_tokens
```

| 테이블 | 주요 컬럼 |
|---|---|
| users | id(UUID PK), email(unique), password_hash, display_name, created_at |
| devices | id(UUID PK), name, code(unique, 장치 이름 = AI 의 device_id), owner_user_id(FK→users), registered_at |
| events | id(UUID PK), device_id(FK→devices), event_key, detected_at, received_at, risk_score, risk_level, risk_types(배열), indicators·target_vehicle·clip·meta(JSONB), latitude, longitude, speed_kmh, heading, video_url, thumb_url, video_deleted_at, status, created_at. UNIQUE(device_id, event_key) |
| reports | id(UUID PK), event_id(FK→events), user_id(FK→users), reported_at, memo |
| user_fcm_tokens | id(UUID PK), user_id(FK→users), token(unique), platform, created_at, updated_at |

제약: `risk_types` 는 1개 이상이고 `lane_departure / weaving / swerving / drifting / speed_irregular` 안에서만, `risk_level` 은 `low / medium / high`, `risk_score` 는 0~100, `status` 는 `new / reviewed / reported`(기본 `new`).

### 설계 결정과 이유

- **id를 자동증가가 아닌 UUID로** — 통신이 끊겼다 몰아서 올라올 때 번호가 꼬이지 않게. 처음엔 장치가 만든 UUID 를 그대로 썼는데, AI 형식으로 바꾸면서(2026-11-02) 서버가 매기고 장치 번호는 `event_key` 로 분리했다. AI 번호는 장치끼리 겹칠 수 있기 때문이다.
- **AI 가 정하는 값은 해석하지 않고 저장** — 위험 종류·등급·근거 수치는 AI 가 기준을 바꾸는 값이다. 서버가 의미를 알면 AI 가 바뀔 때마다 서버도 고쳐야 한다. 그래서 `indicators` 등은 JSONB 로 받은 그대로 둔다. 단 앱이 화면에 쓰는 `risk_types`·`risk_level` 은 DB CHECK 로 허용값을 강제한다.
- **시각을 둘로 분리** — `detected_at`(장치가 감지한 시각)과 `received_at`(서버가 받은 시각)은 통신 지연 때문에 다를 수 있다.
- **영상은 URL만 저장** — 파일은 용량이 크므로 스토리지에 두고 DB에는 링크만 남긴다.
- **`ddl-auto: none`** — 스키마는 SQL로 직접 만들었으므로 JPA가 건드리지 않게 한다.
- **FCM 토큰을 users 컬럼이 아니라 별도 테이블로** — 한 사람이 폰을 두 대 쓰거나 기기를 바꿀 수 있다. 컬럼 하나로 두면 새 폰에서 로그인할 때 이전 폰이 알림을 못 받는다. `token` 을 UNIQUE 로 둔 것은 같은 폰이 두 사용자에게 동시에 묶이지 않게 하기 위해서다.

## 알려진 개선 과제

현재 동작에는 지장이 없지만 다음 작업 때 함께 정리할 것들.

- **deviceId가 UUID 그대로 노출** — 앱이 어느 차량인지 알 수 없다. 장치 이름을 함께 내려주도록 변경 필요.

## 다음 할 일

1. **인증 1-B** — 로그인과 JWT 발급. JWT 라이브러리 추가, 비밀키 설정, `LoginRequest`/`LoginResponse`, `JwtProvider`
2. **인증 2단계** — 토큰을 검사하는 필터, `/api/auth/me`, FCM 토큰 등록(`user_fcm_tokens` 사용)
3. 장치·신고 — Device, Report 엔티티와 엔드포인트
4. FCM 푸시 — 이벤트 수신 시 해당 장치 소유자에게 발송
5. DTO / Service 계층 분리, 예외 처리, 유효성 검증
6. React Native 앱 (별도) — 로그인 → 목록(offline-first) → 상세(영상 + 카카오맵) → 신고
7. 배포 — AWS EC2 + RDS, `schema.sql` 그대로 적용 가능

## 외부 의존

- **AI 파트(설만수)** — 라즈베리파이가 보내는 실제 이벤트 JSON 형태가 아직 미확정. 모델 완성 후 전달받아 `events` 필드(특히 `risk_type` 종류, `risk_score` 범위, 번호판·영상 전달 방식)를 확정하고 그에 맞춰 수정한다. 현재 필드는 우리 설계안 기준.
- **영상 전달 방식 미확정** — 장치가 S3 등에 올리고 URL만 주는지, 서버로 업로드하는지에 따라 `video_url` 처리 방식이 달라진다.

## 변경 이력

### 2026-11-02
- **이벤트 형식을 AI 파트 스키마로 변경.** `POST /api/events` 가 AI 이벤트 JSON(snake_case, 중첩)을 그대로 받는다. 위험 종류는 AI 확정안 5개(차선 걸침·사행·스웨빙·표류·속도 불규칙)로, 한 이벤트에 여러 개. 등급(`risk_level`), 판단 근거 수치(`indicators`), 자차 속도·방위각, 클립 정보, 모델 버전을 저장한다. 번호판(`plate_number`)은 AI·HW 모두 만들지 않아 뺐다.
- **id 는 서버가 매기고 중복 판별은 (장치, event_key)** — AI 번호는 장치끼리 겹칠 수 있어서.
- **`devices.code`(장치 이름) 추가** — AI 의 `device_id` 는 `safewatch-dev-001` 같은 이름이라 이 칸으로 장치를 찾는다. `Device` 엔티티·`DeviceRepository` 추가.
- 돌고 있는 DB 용 변경분 `backend/migrations/2026-11-02_ai_event_schema.sql`. **events·reports 를 지우고 새로 만든다**(옛 형식은 옮길 수 없고 전부 테스트 데이터). 시드 3건은 새 형식으로 다시 넣는다.
- 8081 에서 확인: AI 더미 생성기 JSON 3건 그대로 201, 재전송 200, 같은 event_id 를 다른 장치가 보내면 따로 201, 등록 안 된 장치 400, 옛 위험 종류(`drunk_suspect`) 400, 위치·클립 없는 JSON 201, 무시(PATCH) 정상. 테스트 데이터 정리.
- ⚠️ HW 의 `edge/upload/sender.py` 는 옛 형식으로 변환해 보내므로 **이제 400** 을 받는다. 변환 없이 AI JSON 을 그대로 보내도록 바꿔야 한다. `device/send_events.py`, `frontend/dev-console` 도 옛 형식.

### 2026-10-26
- **영상 보관·파기.** 무시하면 즉시, 그 외에는 받은 지 2주 뒤 자동으로 영상 주소를 지운다. `events.video_deleted_at` 칸 추가, `VideoRetentionJob` 추가, 응답에 `videoExpiresAt`·`videoDeletedAt` 추가. 규칙을 확인하는 단위 테스트 `EventVideoRetentionTest` 추가.
- 돌고 있는 DB 용 변경분은 `backend/migrations/2026-10-26_video_deleted_at.sql`. 새 jar 를 올리기 **전에** 먼저 실행해야 한다(칸이 없으면 새 서버가 조회할 때 오류).
- 8081 에서 확인: 15일 전에 받은 신고 이벤트가 서버 기동 시 파기됐고, `reviewed` 로 바꾸자 즉시 파기됐다. 로컬 DB 의 9월 시드 3건도 2주가 지나 함께 파기됐다(영상 주소가 원래 가짜라 영향 없음).

### 2026-09-29
- **장치 → 서버 전송 경로 확인 (네트워크 너머).** 지금까지는 전부 `localhost` 안에서만 돌았다. `device/send_events.py` 를 만들어 LAN 주소로 전송하는 것을 확인했고, 휴대폰 브라우저로도 서버에 닿는 것을 확인했다.
- **통신 단절 복구 검증.** 서버를 내린 16초 동안 4건이 쌓였고, 서버를 다시 올리자 감지 순서대로 한꺼번에 전송돼 하나도 잃지 않았다.
- 한계 확인: 노트북 IP 는 공유기가 다시 나눠주면 바뀐다(실제로 작업 중 `172.21.35.104` → `172.21.98.163` 로 바뀌었다). 주소를 고정하려면 서버를 AWS 로 올려야 한다.

### 2026-09-15
- **인증 1-A 완료 — 회원가입.** Spring Security 도입(`spring-boot-starter-security`), `User` 엔티티와 `UserRepository`, `SignupRequest`/`UserResponse` DTO, `AuthController.signup`, `SecurityConfig` 추가. 비밀번호는 BCrypt 해시로 저장하고 응답에는 해시를 담지 않는다.
- **버그 수정 — 404·409 가 403 으로 나가던 문제.** Spring Security 를 넣으면서 오류 응답용 내부 요청(`DispatcherType.ERROR`)이 막혀 생긴 문제였다. `SecurityConfig` 에 해당 디스패치를 허용하는 한 줄을 추가.
- **오류 사유 문구 누락 수정.** `ApiExceptionHandler` 에 `ResponseStatusException` 처리를 추가해, 상태 코드만 나가던 응답에 사유(`detail`)가 함께 담기도록 했다.
- DB 컨테이너가 멈춰 있어 재시작. 볼륨이 남아 있어 데이터는 그대로였다.

### 2026-09-10
- **`user_fcm_tokens` 테이블 추가.** 푸시 알림을 보낼 폰 주소를 저장한다. users 에 컬럼 하나로 두지 않고 1:N 별도 테이블로 만든 이유는 한 사람이 폰을 두 대 쓰거나 바꿀 수 있기 때문이다. 돌고 있는 DB 에 직접 적용하고 `schema.sql` 과 `docker/initdb/01-schema.sql` 도 같이 고쳤다. 임시 DB 에 파일만으로 스키마를 세워 실제 DB 와 일치하는 것을 확인했다. 시드 데이터는 그대로.
- **응답 DTO 분리.** 컨트롤러가 `Event` 엔티티 대신 `EventResponse` 를 돌려주도록 바꿨다. 엔티티는 DB 저장용, DTO 는 응답용으로 역할을 나눈 것이다. 응답 JSON 필드는 이전과 동일하게 유지했으므로 앱 영향은 없다. 요청 쪽은 이미 `EventCreateRequest` 로 분리돼 있었고, 이번에 응답 쪽 대칭을 맞췄다.
- **`PATCH /api/events/{id}` 구현.** 신고/무시 상태 변경. 이로써 이벤트 API 4개가 모두 완성됐다. 엔티티에 세터를 열지 않고 `changeStatus()` 만 두어 status 외의 값은 바뀔 수 없게 했다.
- **`POST /api/events` 구현.** 장치가 보낸 감지 결과를 등록한다. 요청 DTO(`EventCreateRequest`)에 DB CHECK 제약과 같은 검증 규칙을 걸어 잘못된 요청이 500 대신 400 으로 나가게 했다. 같은 id 재전송은 중복 생성 없이 200 으로 응답한다.
- `GET /api/events/{id}` 상세 조회, 목록 `since` 증분 조회, 목록 최신순 정렬 추가.
- `ApiExceptionHandler` 추가 — 검증 실패와 DB 제약조건 위반을 400 으로 변환.
- Event 엔티티에 생성용 생성자 추가. `receivedAt`/`status`/`createdAt` 처럼 서버가 정하는 값을 한곳에서 채운다.
- `GET /api/events` 404 해결. 원인은 코드가 아니라 **서버 재시작 누락**이었다. 8080에서 돌던 프로세스가 9월 8일 빌드라 그날 작성한 event 패키지가 반영된 적이 없었다. 재빌드 후 정상 동작 확인(시드 3건 반환).
- **URL 규칙 통일.** 이벤트만 `/api/events` 이고 인증·장치·신고는 접두사가 없어 규칙이 갈라져 있었다. `config/WebConfig.java` 를 추가해 `@RestController` 전체에 `/api` 를 자동으로 붙이도록 하고, `EventController` 의 `@RequestMapping` 을 `/events` 로 줄였다. 최종 주소는 `/api/events` 로 동일하며 앱 영향 없음.

### 2026-09-09 (1·2주차 마무리)
- Event 엔티티, EventRepository, EventController 작성.

### 1·2주차
- 데이터 흐름 설계, DB 4개 테이블 설계·생성, API 명세 12개 작성, MVP 범위와 기술 스택 확정.
- Docker PostgreSQL 구축, Spring Boot 프로젝트 생성, 서버–DB 연결 성공.
- 트러블슈팅: Code Runner 대신 Gradle 사용, Java 17→21, 빈 `application.yml` 채움, brew/Docker PostgreSQL 5432 포트 충돌 해결.
