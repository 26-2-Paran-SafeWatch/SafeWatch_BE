-- =============================================================================
-- 개발용 샘플 데이터 (관계 동작 확인용)
-- 고정 UUID 를 써서 관계를 눈으로 따라갈 수 있게 함.
-- =============================================================================

-- 사용자 2명
INSERT INTO users (id, email, password_hash, display_name) VALUES
  ('11111111-1111-1111-1111-111111111111', 'mingyu@ajou.ac.kr', 'HASHED_pw_1', '주민규'),
  ('22222222-2222-2222-2222-222222222222', 'test@ajou.ac.kr',   'HASHED_pw_2', '테스트유저');

-- 장치 2대 (민규가 1호, 2호 소유)
INSERT INTO devices (id, name, owner_user_id) VALUES
  ('aaaaaaaa-0000-0000-0000-000000000001', '민규 차량 1호', '11111111-1111-1111-1111-111111111111'),
  ('aaaaaaaa-0000-0000-0000-000000000002', '민규 차량 2호', '11111111-1111-1111-1111-111111111111');

-- 위험 이벤트 (장치가 생성한 UUID 를 id 로 그대로 사용)
INSERT INTO events
  (id, device_id, detected_at, risk_type, risk_score, latitude, longitude, plate_number, video_url, status) VALUES
  ('e0000000-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001',
   now() - interval '10 min', 'drunk_suspect', 88, 37.2820, 127.0450, '12가 3456',
   'https://storage.example.com/clips/evt_001.mp4', 'new'),
  ('e0000000-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001',
   now() - interval '6 min', 'lane_departure', 72, 37.2795, 127.0435, '34나 7890',
   'https://storage.example.com/clips/evt_002.mp4', 'new'),
  ('e0000000-0000-0000-0000-000000000003', 'aaaaaaaa-0000-0000-0000-000000000002',
   now() - interval '2 min', 'sudden_accel', 65, 37.2760, 127.0410, NULL,
   'https://storage.example.com/clips/evt_003.mp4', 'reported');

-- 신고 기록 (민규가 3번 이벤트를 신고)
INSERT INTO reports (event_id, user_id, memo) VALUES
  ('e0000000-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', '앞차 급가속 위험, 신고합니다');
