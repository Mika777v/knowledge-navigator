-- 意味検索のベクトル（ADR-0007・E13-T3）。条・FAQ・制度の文章を ai-service でベクトルにしたもの（ADR-0006）
-- - 権限の情報（公開範囲・所管部署）はコピーしない。検索のときに元の表と結合し、キーワード検索と同じ AccessPolicy の条件で絞る
-- - 元の行は種類（source_type）ごとに別の表にあるため外部キーは付けない。元がなくなった行は、ベクトルを作るジョブが消す
-- - 次元は 1024 に固定する。次元の違うモデルに替えるときは、マイグレーションで列か表を足す
CREATE TABLE embeddings (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  source_type   varchar(20) NOT NULL,
  source_id     uuid NOT NULL,
  -- 長い条を区切ったときの番号（0 から）
  chunk_no      int NOT NULL DEFAULT 0,
  -- ベクトルを作ったモデル（ai-service の /v1/info の model）。検索は今のモデルの行だけを使う
  model         varchar(100) NOT NULL,
  -- ベクトルにした文章の SHA-256（16進）。変わったものだけ作り直す
  content_hash  char(64) NOT NULL,
  embedding     vector(1024) NOT NULL,
  created_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (source_type, source_id, chunk_no, model),
  CONSTRAINT ck_embeddings_source_type CHECK (source_type IN ('SECTION', 'FAQ', 'PROGRAM')),
  CONSTRAINT ck_embeddings_chunk_no CHECK (chunk_no >= 0)
);
-- 近いベクトルを探す索引（HNSW・コサイン距離）。モデルごとの部分索引にする。
-- 別のモデルを使うときは、そのモデルの部分索引をマイグレーションで足す（ADR-0007）
CREATE INDEX idx_embeddings_hnsw_bge_m3 ON embeddings
  USING hnsw (embedding vector_cosine_ops) WHERE model = 'bge-m3';
COMMENT ON TABLE embeddings IS '条・FAQ・制度の文章のベクトル（ADR-0007）。権限は持たず、検索時に元の表と結合して絞る';
