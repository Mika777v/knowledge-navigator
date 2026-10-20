-- 拡張を作成する（初回起動時のみ実行）。
-- アプリの DB ユーザーは superuser にしない前提のため、拡張はここで作っておき、
-- Flyway 側の CREATE EXTENSION IF NOT EXISTS は何もしない状態にする。

-- POSTGRES_DB で作られたデータベース
CREATE EXTENSION IF NOT EXISTS pg_bigm;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 以後 CREATE DATABASE で作るデータベース（テスト用など）にも引き継ぐ
\connect template1
CREATE EXTENSION IF NOT EXISTS pg_bigm;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
