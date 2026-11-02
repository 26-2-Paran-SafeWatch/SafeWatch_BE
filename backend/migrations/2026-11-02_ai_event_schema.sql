-- 2026-11-02 이벤트 형식을 AI 파트 스키마(SafeWatch_AI docs/event-schema.md v0.7, 5.1 확정안)로 변경
-- 이미 돌고 있는 DB 에 적용하는 변경분. 새로 만드는 DB 는 schema.sql 에 이미 들어 있다.
--
-- ⚠️ events 와 reports 를 지우고 새로 만든다. 옛 형식(risk_type 하나, plate_number 등) 데이터는
--    새 형식으로 옮길 방법이 없고, 2026-11-02 기준 전부 테스트 데이터라 버린다. 시드 3건을 새 형식으로 다시 넣는다.
-- 한 트랜잭션이라 중간에 실패하면 아무것도 바뀌지 않는다.
BEGIN;

-- 1. 장치 이름 칸. AI 이벤트의 device_id(예: safewatch-dev-001)를 이 값으로 찾는다
ALTER TABLE devices ADD COLUMN IF NOT EXISTS code TEXT;
UPDATE devices SET code = 'safewatch-dev-001'   WHERE id = 'aaaaaaaa-0000-0000-0000-000000000001' AND code IS NULL;
UPDATE devices SET code = 'safewatch-dummy-001' WHERE id = 'aaaaaaaa-0000-0000-0000-000000000002' AND code IS NULL;
UPDATE devices SET code = id::text WHERE code IS NULL;   -- 그 밖의 장치는 일단 UUID 를 이름으로
ALTER TABLE devices ALTER COLUMN code SET NOT NULL;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'devices_code_key') THEN
    ALTER TABLE devices ADD CONSTRAINT devices_code_key UNIQUE (code);
  END IF;
END $$;

-- 2. events (와 events 를 참조하는 reports) 를 새 형식으로
DROP TABLE IF EXISTS reports;
DROP TABLE IF EXISTS events;

CREATE TABLE events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id      UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    event_key      TEXT NOT NULL,                 -- AI 의 event_id. 장치 안에서만 고유
    detected_at    TIMESTAMPTZ NOT NULL,          -- AI 의 timestamp (장치 시계)
    received_at    TIMESTAMPTZ NOT NULL DEFAULT now(),  -- 서버가 받은 시각
    risk_score     SMALLINT NOT NULL CHECK (risk_score BETWEEN 0 AND 100),  -- 음주 의심 점수
    risk_level     TEXT NOT NULL CHECK (risk_level IN ('low','medium','high')),  -- 등급 구간은 AI 가 정한다
    risk_types     TEXT[] NOT NULL                -- 동시에 여러 개. NHTSA 차로 유지 단서
                   CHECK (cardinality(risk_types) >= 1 AND risk_types <@
                          ARRAY['lane_departure','weaving','swerving','drifting','speed_irregular']),
    indicators     JSONB,                         -- 판단 근거 수치. 판단에 쓴 것만 들어온다
    target_vehicle JSONB,                         -- 추적 정보 (디버깅·신뢰도 참고)
    latitude       DOUBLE PRECISION,              -- GPS 를 못 잡으면 위치 전체가 비어서 온다
    longitude      DOUBLE PRECISION,
    speed_kmh      DOUBLE PRECISION,              -- 자차 속도
    heading        DOUBLE PRECISION,              -- 자차 진행 방위각 0~360
    clip           JSONB,                         -- 영상 클립 정보(길이·해상도·블러 여부). 파일 자체는 아님
    video_url      TEXT,                          -- 영상 파일은 스토리지에, DB엔 링크만 (전송 방식 결정 후 채워짐)
    thumb_url      TEXT,
    video_deleted_at TIMESTAMPTZ,               -- 영상 파기 시각 (NULL = 보관 중). 무시하면 즉시, 아니면 2주 뒤 서버가 파기
    meta           JSONB,                         -- 모델·규칙 버전 (언제 어떤 모델이 잡았는지 추적)
    status         TEXT NOT NULL DEFAULT 'new'
                   CHECK (status IN ('new','reviewed','reported')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (device_id, event_key)                 -- 재전송 중복 판별
);
CREATE INDEX idx_events_device      ON events(device_id);
CREATE INDEX idx_events_received_at ON events(received_at DESC);  -- 최신순 조회/증분 동기화

CREATE TABLE reports (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    memo        TEXT                            -- 사용자가 덧붙인 메모(선택)
);
CREATE INDEX idx_reports_event ON reports(event_id);
CREATE INDEX idx_reports_user  ON reports(user_id);

-- 위험 이벤트 (AI 이벤트 스키마 형식. 영상은 아직 전송 방식이 미정이라 클립 정보만 있다)
INSERT INTO events
  (id, device_id, event_key, detected_at, risk_score, risk_level, risk_types, indicators,
   latitude, longitude, speed_kmh, heading, clip, meta, status) VALUES
  ('e0000000-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'evt_seed_001',
   now() - interval '10 min', 88, 'high', ARRAY['lane_departure','weaving'],
   '{"observation_window_sec": 10.0, "lane_offset_max_ratio": 0.38, "lane_departure_duration_sec": 2.8, "direction_changes_count": 4}',
   37.2820, 127.0450, 62.5, 271.3,
   '{"filename": "evt_seed_001.mp4", "duration_sec": 15, "pre_event_sec": 10, "post_event_sec": 5, "resolution": "1280x720", "fps": 15, "blurred": true}',
   '{"model_version": "seed", "rule_version": "seed"}', 'new'),
  ('e0000000-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001', 'evt_seed_002',
   now() - interval '6 min', 64, 'medium', ARRAY['drifting','swerving'],
   '{"observation_window_sec": 10.0, "drift_duration_sec": 4.1, "lateral_velocity_peak_mps": 1.4}',
   37.2795, 127.0435, 48.0, 90.0, NULL,
   '{"model_version": "seed", "rule_version": "seed"}', 'new'),
  ('e0000000-0000-0000-0000-000000000003', 'aaaaaaaa-0000-0000-0000-000000000002', 'evt_seed_003',
   now() - interval '2 min', 45, 'medium', ARRAY['speed_irregular','weaving'],
   '{"observation_window_sec": 10.0, "expansion_rate_peak_per_sec": 0.35, "direction_changes_count": 2}',
   NULL, NULL, NULL, NULL, NULL,
   '{"model_version": "seed", "rule_version": "seed"}', 'reported');

-- 신고 기록 (민규가 3번 이벤트를 신고)
INSERT INTO reports (event_id, user_id, memo) VALUES
  ('e0000000-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', '앞차 속도가 불규칙하고 좌우로 흔들림, 신고합니다');

COMMIT;
