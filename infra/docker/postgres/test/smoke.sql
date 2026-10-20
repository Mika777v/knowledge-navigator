-- イメージのスモークテスト。失敗したら例外で止まる（psql -v ON_ERROR_STOP=1 で実行）
-- データは架空のもの

-- 1. 拡張が有効になっていること
DO $$
DECLARE
  ext text;
BEGIN
  FOREACH ext IN ARRAY ARRAY['pg_bigm', 'vector', 'pgcrypto'] LOOP
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = ext) THEN
      RAISE EXCEPTION 'extension % is not installed', ext;
    END IF;
  END LOOP;
END $$;

-- 2. DB の文字コードが UTF8 であること（pg_bigm の前提）
DO $$
DECLARE
  enc text;
BEGIN
  SELECT pg_encoding_to_char(encoding) INTO enc FROM pg_database WHERE datname = current_database();
  IF enc <> 'UTF8' THEN
    RAISE EXCEPTION 'database encoding is %, expected UTF8', enc;
  END IF;
END $$;

-- 3. 日本語の部分一致が bigm インデックスで引けること
DROP TABLE IF EXISTS smoke_sections;
CREATE TABLE smoke_sections (
  id   int PRIMARY KEY,
  body text NOT NULL
);
INSERT INTO smoke_sections (id, body)
SELECT g, '第' || g || '条（雑則）この規程に定めのない事項は、別に定める。'
FROM generate_series(1, 2000) AS g;
INSERT INTO smoke_sections (id, body) VALUES
  (10001, '第5条（結婚祝金）社員が婚姻したときは、結婚祝金として30,000円を支給する。'),
  (10002, '第32条（特別休暇）本人が結婚するときは、連続5日の特別休暇を与える。'),
  (10003, '第12条（在宅勤務）在宅勤務を希望する者は、所属長に申請する。');
CREATE INDEX idx_smoke_sections_bigm ON smoke_sections USING gin (body gin_bigm_ops);
ANALYZE smoke_sections;

DO $$
DECLARE
  plan text := '';
  r record;
  hits int;
BEGIN
  SET LOCAL enable_seqscan = off;
  FOR r IN EXECUTE $q$EXPLAIN SELECT id FROM smoke_sections WHERE body LIKE likequery('結婚')$q$ LOOP
    plan := plan || r."QUERY PLAN" || E'\n';
  END LOOP;
  IF plan NOT LIKE '%idx_smoke_sections_bigm%' THEN
    RAISE EXCEPTION E'bigm index is not used:\n%', plan;
  END IF;

  SELECT count(*) INTO hits FROM smoke_sections WHERE body LIKE likequery('結婚');
  IF hits <> 2 THEN
    RAISE EXCEPTION 'expected 2 hits for 結婚, got %', hits;
  END IF;

  SELECT count(*) INTO hits FROM smoke_sections WHERE body LIKE likequery('在宅勤務');
  IF hits <> 1 THEN
    RAISE EXCEPTION 'expected 1 hit for 在宅勤務, got %', hits;
  END IF;

  IF bigm_similarity('結婚祝金', '結婚') <= 0 THEN
    RAISE EXCEPTION 'bigm_similarity returned non-positive value';
  END IF;
END $$;

-- 4. ベクトルの近傍検索（HNSW）が動くこと
DROP TABLE IF EXISTS smoke_embeddings;
CREATE TABLE smoke_embeddings (
  id        int PRIMARY KEY,
  embedding vector(3) NOT NULL
);
INSERT INTO smoke_embeddings VALUES
  (1, '[1, 0, 0]'),
  (2, '[0, 1, 0]'),
  (3, '[0, 0, 1]');
CREATE INDEX idx_smoke_embeddings_hnsw ON smoke_embeddings USING hnsw (embedding vector_cosine_ops);

DO $$
DECLARE
  nearest int;
BEGIN
  SELECT id INTO nearest FROM smoke_embeddings ORDER BY embedding <=> '[0.1, 0.9, 0]' LIMIT 1;
  IF nearest <> 2 THEN
    RAISE EXCEPTION 'expected nearest id 2, got %', nearest;
  END IF;
END $$;

-- 5. pgcrypto（uuid 生成）
DO $$
BEGIN
  IF gen_random_uuid() IS NULL THEN
    RAISE EXCEPTION 'gen_random_uuid failed';
  END IF;
END $$;

DROP TABLE smoke_sections;
DROP TABLE smoke_embeddings;

SELECT 'SMOKE TEST PASSED' AS result,
       (SELECT extversion FROM pg_extension WHERE extname = 'pg_bigm') AS pg_bigm,
       (SELECT extversion FROM pg_extension WHERE extname = 'vector')  AS pgvector,
       current_setting('server_version') AS postgres;
