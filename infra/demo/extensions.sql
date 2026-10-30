-- デモ環境（RDS）の DB に拡張を作る。scripts/demo/up.sh・reset.sh が psql で流す（何度流してもよい）。
-- ローカル・CI の DB は、イメージの初期化（infra/docker/postgres/initdb/00-extensions.sql）で同じものを作っている
CREATE EXTENSION IF NOT EXISTS pg_bigm;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
