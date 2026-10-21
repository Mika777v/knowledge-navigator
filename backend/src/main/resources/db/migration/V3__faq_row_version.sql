-- V3: FAQ も文書・制度と同じく楽観ロック（If-Match）で更新する（#33）
ALTER TABLE faqs ADD COLUMN row_version int NOT NULL DEFAULT 0;
