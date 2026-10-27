-- =============================================================================
-- SafeWatch 관계형 스키마 (PostgreSQL)
-- 위험운전 이벤트 수집/전달 서비스의 서버 DB.
--
-- 관계(요약):
--   users   1 ── N  devices    (한 사용자가 여러 차량 장치를 소유)
--   devices 1 ── N  events     (한 장치가 여러 위험 이벤트를 생성)
--   events  1 ── N  reports    (한 이벤트에 여러 신고 기록)
--   users   1 ── N  reports    (한 사용자가 여러 신고를 남김)
--   users   1 ── N  user_fcm_tokens (한 사용자가 여러 폰에서 알림 수신)
--   => reports 는 events 와 users 를 잇는 기록 테이블
-- =============================================================================

-- 재실행 편의를 위해 있으면 지우고 다시 생성 (개발용)
DROP TABLE IF EXISTS user_fcm_tokens CASCADE;
DROP TABLE IF EXISTS reports CASCADE;
DROP TABLE IF EXISTS events  CASCADE;
DROP TABLE IF EXISTS devices CASCADE;
DROP TABLE IF EXISTS users   CASCADE;

-- -----------------------------------------------------------------------------
-- users : 앱 사용자 계정 (민규 담당 - 계정 관리)
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT NOT NULL UNIQUE,          -- 로그인 ID
    password_hash TEXT NOT NULL,                 -- 비밀번호는 반드시 해시로 저장(평문 금지)
    display_name  TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- -----------------------------------------------------------------------------
-- devices : SafeWatch 장치 (차량에 달린 라즈베리파이 유닛)
--   owner_user_id 로 "이 장치의 알림을 받을 사용자" 를 연결.
-- -----------------------------------------------------------------------------
CREATE TABLE devices (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          TEXT NOT NULL,                 -- 예: "민규 차량 1호"
    owner_user_id UUID REFERENCES users(id) ON DELETE SET NULL,  -- 주인이 탈퇴해도 장치는 남김
    registered_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_devices_owner ON devices(owner_user_id);

-- -----------------------------------------------------------------------------
-- user_fcm_tokens : 푸시 알림을 보낼 폰의 주소(FCM 토큰)
--   users 와 1:N 인 이유 - 한 사람이 폰을 두 대 쓰거나 기기를 바꿀 수 있다.
--   users 에 컬럼 하나로 두면 새 폰에서 로그인할 때 이전 폰이 알림을 못 받는다.
--   token 을 UNIQUE 로 둔 이유 - 같은 폰이 두 사용자에게 동시에 묶이면 안 된다.
--   폰 주인이 바뀌면 그 토큰의 user_id 를 새 주인으로 옮긴다.
-- -----------------------------------------------------------------------------
CREATE TABLE user_fcm_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,  -- 탈퇴하면 토큰도 함께 삭제
    token      TEXT NOT NULL UNIQUE,                    -- FCM 이 발급한 기기 주소
    platform   TEXT CHECK (platform IN ('android','ios')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()       -- FCM 토큰은 갱신될 수 있음
);
CREATE INDEX idx_user_fcm_tokens_user ON user_fcm_tokens(user_id);

-- -----------------------------------------------------------------------------
-- events : 위험운전 이벤트 (핵심 테이블)
--   id 는 장치(라즈베리파이)가 생성한 UUID 를 그대로 사용 → 통신 지연/중복에 강함.
--   risk_type / status 는 별도 테이블로 정규화하지 않고 CHECK 제약으로 값만 강제.
-- -----------------------------------------------------------------------------
CREATE TABLE events (
    id           UUID PRIMARY KEY,              -- 장치가 생성한 UUID (서버가 매기지 않음)
    device_id    UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    detected_at  TIMESTAMPTZ NOT NULL,          -- AI가 감지한 시각(장치 시계)
    received_at  TIMESTAMPTZ NOT NULL DEFAULT now(),  -- 서버가 받은 시각
    risk_type    TEXT NOT NULL
                 CHECK (risk_type IN ('drunk_suspect','lane_departure','sudden_accel','swerving')),
    risk_score   SMALLINT NOT NULL CHECK (risk_score BETWEEN 0 AND 100),
    latitude     DOUBLE PRECISION,
    longitude    DOUBLE PRECISION,
    plate_number TEXT,
    video_url    TEXT,                          -- 영상 파일은 스토리지에, DB엔 링크만
    thumb_url    TEXT,
    video_deleted_at TIMESTAMPTZ,               -- 영상 파기 시각 (NULL = 보관 중). 무시하면 즉시, 아니면 2주 뒤 서버가 파기
    status       TEXT NOT NULL DEFAULT 'new'
                 CHECK (status IN ('new','reviewed','reported')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_events_device      ON events(device_id);
CREATE INDEX idx_events_received_at ON events(received_at DESC);  -- 최신순 조회/증분 동기화

-- -----------------------------------------------------------------------------
-- reports : 사용자가 특정 이벤트를 '신고' 처리한 기록
--   앱에서 신고 버튼을 누르면 여기에 한 줄 쌓임. (누가, 어떤 이벤트를, 언제)
-- -----------------------------------------------------------------------------
CREATE TABLE reports (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    memo        TEXT                            -- 사용자가 덧붙인 메모(선택)
);
CREATE INDEX idx_reports_event ON reports(event_id);
CREATE INDEX idx_reports_user  ON reports(user_id);
