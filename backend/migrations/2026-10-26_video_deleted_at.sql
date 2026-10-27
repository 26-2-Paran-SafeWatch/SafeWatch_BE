-- 2026-10-26 영상 보관·파기 정책 (노션 9,10주차)
-- 이미 돌고 있는 DB 에 적용하는 변경분. 새로 만드는 DB 는 schema.sql 에 이미 들어 있다.
-- 여러 번 실행해도 안전하다 (IF NOT EXISTS).
ALTER TABLE events ADD COLUMN IF NOT EXISTS video_deleted_at TIMESTAMPTZ;
