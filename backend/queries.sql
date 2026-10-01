-- =============================================================================
-- 관계가 실제로 동작하는지 보여주는 예시 쿼리 (앱이 쓸 대표 조회들)
-- =============================================================================

-- [Q1] 앱 메인 화면: 특정 사용자의 모든 장치에서 온 위험 이벤트 목록(최신순)
--   users → devices → events 3단 조인
SELECT u.display_name, d.name AS device, e.risk_type, e.risk_score, e.status,
       e.detected_at
FROM events e
JOIN devices d ON d.id = e.device_id
JOIN users   u ON u.id = d.owner_user_id
WHERE u.id = '11111111-1111-1111-1111-111111111111'
ORDER BY e.received_at DESC;

-- [Q2] 신고 내역: 어떤 사용자가 어떤 이벤트를 신고했는지
--   reports → events, users 조인
SELECT u.display_name AS 신고자, e.risk_type, e.plate_number, r.memo, r.reported_at
FROM reports r
JOIN events e ON e.id = r.event_id
JOIN users  u ON u.id = r.user_id
ORDER BY r.reported_at DESC;

-- [Q3] 장치별 위험 이벤트 건수 통계
SELECT d.name AS device, COUNT(e.id) AS event_count, AVG(e.risk_score)::INT AS avg_score
FROM devices d
LEFT JOIN events e ON e.device_id = d.id
GROUP BY d.name
ORDER BY event_count DESC;

-- [Q4] 제약조건 확인용(실패해야 정상): 잘못된 risk_score 는 거부됨
-- INSERT INTO events (id, device_id, detected_at, risk_type, risk_score)
-- VALUES (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000001', now(), 'drunk_suspect', 150);
--   → ERROR: violates check constraint (risk_score BETWEEN 0 AND 100)
