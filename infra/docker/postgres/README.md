# PostgreSQL イメージ（pgvector + pg_bigm）

社内規程ナビで使う PostgreSQL のコンテナイメージ。
判断の背景は [ADR-0002](../../../docs/adr/0002-japanese-search-pg-bigm.md) を参照。

| 項目 | 内容 |
|---|---|
| ベース | `pgvector/pgvector:0.8.7-pg17-bookworm`（PostgreSQL 17 公式イメージ + pgvector） |
| 追加拡張 | pg_bigm（日本語の部分一致を 2-gram の GIN インデックスで高速化） |
| 初期化 | `initdb/00-extensions.sql` が初回起動時に `pg_bigm` `vector` `pgcrypto` を作成（`POSTGRES_DB` と `template1`） |

## 構成

```
infra/docker/postgres/
├── Dockerfile              # 2段階ビルド（pg_bigm をビルド → 成果物だけコピー）
├── initdb/
│   └── 00-extensions.sql   # 拡張の作成
└── test/
    ├── smoke.sql           # 拡張・日本語部分一致・bigm インデックス利用・ベクトル検索の確認
    └── run-smoke.sh        # ビルド → 起動 → smoke.sql 実行
```

## 使い方

```bash
# ビルド
docker build -t knowledge-navigator/postgres infra/docker/postgres

# スモークテスト（ビルドも行う）
infra/docker/postgres/test/run-smoke.sh
```

通常は compose（#24）から使う。単体で起動する場合:

```bash
docker run -d --name kn-postgres -p 5432:5432 \
  -e POSTGRES_USER=knavi -e POSTGRES_PASSWORD=knavi -e POSTGRES_DB=knavi \
  knowledge-navigator/postgres
```

## pg_bigm の使い方（例）

```sql
CREATE INDEX idx_sections_search_bigm ON sections USING gin (search_text gin_bigm_ops);

-- 部分一致（likequery は % や _ をエスケープしてくれる）
SELECT id FROM sections WHERE search_text LIKE likequery('結婚');

-- 類似度（0〜1）
SELECT bigm_similarity('結婚祝金', '結婚');
```

## 更新するとき

| 対象 | 手順 |
|---|---|
| ベースイメージ（pgvector / PostgreSQL のマイナー版） | Dependabot が PR を作る。`ci-db` ワークフローのスモークテストが通ればマージ |
| pg_bigm | `PG_BIGM_VERSION` と `PG_BIGM_SHA256` を両方更新する。SHA-256 は `curl -fsSL <tarball URL> \| sha256sum` で求めるか、`ci-db` の「Verify pg_bigm checksum」が不一致時に PR の注釈へ実際の値を出すのでそれを使う |
| PostgreSQL のメジャー版 | タグを `pg18` 系に変え、pg_bigm がそのメジャー版に対応しているかリリースノートで確認する。既存データはダンプ/リストアが必要 |

## 注意

- `initdb/` のスクリプトは **データディレクトリが空のときだけ** 実行される。既存ボリュームがあると再実行されない（作り直すときは `docker compose down -v`）。
- マネージド PostgreSQL（RDS / Cloud SQL / Azure）で pg_bigm が使えるかは環境ごとに確認が必要（ADR-0002「見直す条件」）。
