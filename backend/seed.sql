-- =============================================================================
-- 개발용 샘플 데이터 (관계 동작 확인용)
-- 고정 UUID 를 써서 관계를 눈으로 따라갈 수 있게 함.
-- =============================================================================

-- 사용자 2명
INSERT INTO users (id, email, password_hash, display_name) VALUES
  ('11111111-1111-1111-1111-111111111111', 'mingyu@ajou.ac.kr', 'HASHED_pw_1', '주민규'),
  ('22222222-2222-2222-2222-222222222222', 'test@ajou.ac.kr',   'HASHED_pw_2', '테스트유저');

-- 장치 2대 (민규가 1호, 2호 소유)
--   code 는 AI 쪽 기본값에 맞췄다: configs/default.yaml 의 device_id, scripts/generate_dummy_event.py 의 --device-id
INSERT INTO devices (id, name, code, owner_user_id) VALUES
  ('aaaaaaaa-0000-0000-0000-000000000001', '민규 차량 1호', 'safewatch-dev-001',   '11111111-1111-1111-1111-111111111111'),
  ('aaaaaaaa-0000-0000-0000-000000000002', '민규 차량 2호', 'safewatch-dummy-001', '11111111-1111-1111-1111-111111111111');

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
