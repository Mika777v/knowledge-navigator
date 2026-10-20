-- compose で起動した DB の設定確認（ci-db ワークフローで実行）
DO $$
DECLARE
  provider "char";
BEGIN
  IF (SELECT count(*) FROM pg_extension WHERE extname IN ('pg_bigm', 'vector', 'pgcrypto')) <> 3 THEN
    RAISE EXCEPTION 'extensions are missing';
  END IF;

  SELECT datlocprovider INTO provider FROM pg_database WHERE datname = current_database();
  IF provider <> 'b' THEN
    RAISE EXCEPTION 'builtin locale provider is not used (got %)', provider;
  END IF;

  IF current_setting('TimeZone') <> 'Asia/Tokyo' THEN
    RAISE EXCEPTION 'timezone is %, expected Asia/Tokyo', current_setting('TimeZone');
  END IF;
END $$;

SELECT 'COMPOSE CHECK PASSED' AS result;
