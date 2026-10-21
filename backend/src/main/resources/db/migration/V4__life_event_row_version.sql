-- V4: きっかけも管理画面から編集するため、文書・制度・FAQ と同じく楽観ロック（If-Match）にする（E06-T2）
ALTER TABLE life_events ADD COLUMN row_version int NOT NULL DEFAULT 0;
ALTER TABLE life_events ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
