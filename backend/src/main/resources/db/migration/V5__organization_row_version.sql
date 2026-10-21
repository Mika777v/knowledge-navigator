-- V5: 部署・窓口・ユーザーも管理画面（システム管理者）から編集するため、楽観ロック（If-Match）にする（E09-T4）
ALTER TABLE departments ADD COLUMN row_version int NOT NULL DEFAULT 0;
ALTER TABLE contacts ADD COLUMN row_version int NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN row_version int NOT NULL DEFAULT 0;
