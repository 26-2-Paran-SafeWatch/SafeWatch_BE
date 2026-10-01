SafeWatch 백엔드 — 작업 인수인계 (Claude Code용)

이 문서는 지금까지 진행 상황과 앞으로 할 일을 정리한 것이다. 먼저 "지금 당장 고칠 것" 섹션의 버그부터 해결하고, 그다음 "로드맵" 순서대로 진행하면 된다.

1. 프로젝트 개요
SafeWatch: 차량 카메라 영상을 Edge AI(라즈베리파이)가 분석해 전방 차량의 음주·난폭운전을 감지하고, 그 위험 정보를 사용자 모바일 앱까지 전달하는 서비스.
내(주민규) 담당: 앱 + 백엔드. "AI가 감지한 위험 정보를 사용자에게 전달하는 서비스 플랫폼".
전체 데이터 흐름: 라즈베리파이(Edge AI) → 클라우드 서버 → 앱 → 폰 로컬 DB.
앱은 서버에서 받은 데이터를 폰 로컬 DB에 저장(offline-first) — 통신이 자주 끊기는 차량 환경 대응.
2. 기술 스택 (확정)
앱: Flutter (안드로이드/iOS 공용) — 이 레포는 백엔드. 앱은 별도.
백엔드: Spring Boot (Java 21), Gradle
DB: PostgreSQL (로컬은 Docker 컨테이너, 배포는 추후 AWS RDS)
지도: 카카오맵 (앱 쪽, 백엔드 API 불필요)
푸시: FCM (Firebase Cloud Messaging)
인증: JWT (아직 미구현)
3. 현재 상태 (무엇이 되어 있나)
 Docker로 PostgreSQL 실행 (/docker/docker-compose.yml), 스키마·시드 자동 로드
 Spring Boot 프로젝트 생성 (/backend), application.yml로 DB 연결 성공
 Event 엔티티 + EventRepository 작성
 EventController(GET /api/events) 작성
 BUG: GET /api/events가 404 (아래 "지금 당장 고칠 것" 참고) ← 최우선
 나머지 API 전부 미구현
 Flutter 앱 미착수
4. 실행 방법
bash
# 1) DB (Docker) — /docker 폴더에서
docker compose up -d
# (주의) 로컬에 brew PostgreSQL이 켜져 있으면 5432 포트 충돌 → 반드시 끄기:
#   brew services stop postgresql@18

# 2) 서버 — /backend 폴더에서
./gradlew bootRun     # http://localhost:8080

# 3) DB 직접 확인
docker exec -it safewatch-db psql -U safewatch -d safewatch -c "SELECT COUNT(*) FROM events;"  # 3
DB 접속: host localhost, port 5432, db safewatch, user safewatch, pw safewatch
application.yml은 ddl-auto: none (스키마는 SQL로 이미 생성, JPA가 건드리지 않음)
5. DB 스키마 (PostgreSQL, 이미 생성됨)

/docker/initdb/01-schema.sql에 정의. 4개 테이블 + FK.

users: id(UUID PK), email(unique), password_hash, display_name, created_at
devices: id(UUID PK), name, owner_user_id(FK→users.id), registered_at
events: id(UUID PK), device_id(FK→devices.id), detected_at, received_at, risk_type(CHECK: drunk_suspect/lane_departure/sudden_accel/swerving), risk_score(0~100), latitude, longitude, plate_number, video_url, thumb_url, status(CHECK: new/reviewed/reported, default new), created_at
reports: id(UUID PK), event_id(FK→events.id), user_id(FK→users.id), reported_at, memo

관계: users 1—N devices, devices 1—N events, events 1—N reports, users 1—N reports. 설계 결정: id는 UUID(장치 생성), 시각은 detected_at/received_at 2개, 영상은 URL만 저장, risk_type/status는 CHECK 제약.

6. API 명세 (12개) — 구현 상태
분류	기능	Method	URI	상태
인증	회원가입	POST	/auth/signup	미구현
인증	로그인	POST	/auth/login	미구현
인증	내 정보 조회	GET	/auth/me	미구현
인증	FCM 토큰 등록	POST	/auth/fcm-token	미구현
이벤트	위험 이벤트 업로드	POST	/api/events	미구현
이벤트	위험 이벤트 목록 조회	GET	/api/events?since=	작성했으나 404 (버그)
이벤트	이벤트 상세 조회	GET	/api/events/{id}	미구현
이벤트	이벤트 상태 변경	PATCH	/api/events/{id}	미구현
장치	내 장치 목록 조회	GET	/devices	미구현
장치	장치 등록	POST	/devices	미구현
신고	신고 등록	POST	/reports	미구현
신고	내 신고 목록	GET	/reports	미구현

인증: 로그인 시 JWT 발급, 이후 요청 헤더 Authorization: Bearer <token>. 이벤트 업로드(POST /events)는 라즈베리파이(장치)가 호출 + 서버가 FCM 푸시 발송(추후).

7. 코드 구조 / 컨벤션
base package: com.safewatch.server
현재: com.safewatch.server.event 에 Event, EventRepository, EventController
권장 구조: 기능별 패키지(event/auth/device/report), 계층은 Controller → Service → Repository. 지금은 Controller가 Repository를 바로 호출하는 최소 형태 → 필요 시 Service 계층 추가.
응답은 엔티티를 그대로 반환 중 → 추후 DTO로 분리 권장(특히 auth에서 password_hash 노출 방지).
Lombok 사용 중(@Getter 등).
참고: Event 엔티티는 events 테이블에 이렇게 매핑됨

컬럼명이 snake_case라 @Column(name="...")로 매핑. device_id는 지금 관계(@ManyToOne) 대신 단순 UUID deviceId 필드로 매핑(1차 단순화). 타임스탬프는 OffsetDateTime, risk_score는 Integer.

8. ★ 지금 당장 고칠 것: GET /api/events 404

EventController(GET /api/events)까지 작성했는데 브라우저에서 http://localhost:8080/api/events 접속 시 Whitelabel 404가 뜬다. 서버 자체는 정상 기동됨.

의심 순서 (확인·수정 요망):

EventController.java가 실제로 /backend/src/main/java/com/safewatch/server/event/ 안에 있고 package com.safewatch.server.event; 선언이 맞는지.
파일 저장 후 서버를 재시작했는지(./gradlew bootRun 재실행). 기동 로그에 Found 1 JPA repository interfaces와 컨트롤러 매핑이 잡히는지.
EventController 내용/애노테이션(@RestController, @RequestMapping("/api/events"), @GetMapping)이 올바른지, 컴파일 에러는 없는지.
클린 빌드로 확인: ./gradlew clean bootRun.
그래도 안 되면 curl -i localhost:8080/api/events의 상태코드와 기동 로그 전체를 확인해 원인 특정.

기대 결과: events 3건이 JSON 배열로 반환.

9. 로드맵 (다음 할 일 순서)
위 404 버그 해결 → GET /api/events 동작 확인.
이벤트 나머지: POST /api/events(등록), GET /api/events/{id}(상세), PATCH /api/events/{id}(status 변경). since 파라미터로 증분 조회 지원.
인증: User 엔티티/Repository, 회원가입·로그인(BCrypt 해시 + JWT 발급), Security 필터, GET /auth/me, POST /auth/fcm-token.
장치/신고: Device, Report 엔티티 + 엔드포인트. events.device_id, reports를 관계로 연결(선택).
FCM 푸시: POST /events 수신 시 해당 장치 owner에게 푸시 발송.
DTO/Service 계층 정리, 예외 처리, 유효성 검증.
(별도 레포/폴더) Flutter 앱: 로그인 → 이벤트 리스트(offline-first, sqflite) → 상세(영상+카카오맵) → 신고, + FCM 수신.
배포: 서버 AWS EC2, DB AWS RDS(PostgreSQL). schema.sql 그대로 적용 가능.
10. 외부 의존 / 협업
만수님(AI 개발): 라즈베리파이가 감지한 이벤트 결과의 실제 JSON 형태를 받아 events 필드(특히 risk_type 종류, risk_score 범위, 번호판·영상 전달 방식)를 최종 확정해야 함. 현재 필드는 우리 설계안 기준.
영상 전달 방식 미확정: 장치가 스토리지(S3 등)에 올리고 URL만 주는지, 서버로 업로드하는지 → 정해지면 video_url 처리/재생용 엔드포인트 결정.

작성 기준: 서버–DB 연결 성공, 첫 API(GET /events) 작성 직후 404 디버깅 단계.