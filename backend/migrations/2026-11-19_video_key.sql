-- 2026-11-19 영상 클립을 S3 에 저장 (노션 11,12주차)
-- video_url(주소) → video_key(S3 파일 이름). 버킷이 비공개라 주소는 서버가 볼 때마다 임시로 만든다.
-- 지금까지 이 칸에 들어간 값은 없거나 가짜 주소라 그대로 이름만 바꾼다. 여러 번 실행해도 안전하다.
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_name = 'events' AND column_name = 'video_url') THEN
    ALTER TABLE events RENAME COLUMN video_url TO video_key;
  END IF;
END $$;
