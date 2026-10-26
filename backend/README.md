# backend（Spring Boot）

社内規程ナビのコア API。認証・認可・ドメイン・検索のオーケストレーションを担う（[ADR-0001](../docs/adr/0001-modular-monolith-spring-boot.md)）。

| 項目 | 内容 |
|---|---|
| 言語 / フレームワーク | Java 21 / Spring Boot 4.1 |
| ビルド | Gradle 9（Kotlin DSL、バージョンは `gradle/libs.versions.toml`） |
| 構成 | モジュラーモノリス（Spring Modulith で境界を検証） |
| DB | PostgreSQL 17 + pg_bigm + pgvector（Flyway でマイグレーション） |
| API 仕様 | springdoc（`/v3/api-docs`、`/swagger-ui.html`） |
| フォーマット | Spotless + palantir-java-format |

## モジュール

| パッケージ | 責務 |
|---|---|
| `shared` | 共通部品（ID・時刻・例外・認可ポリシー）。どのモジュールからも参照可 |
| `organization` | 部署・窓口・ユーザー |
| `content` | 文書・版・セクション・制度・きっかけ・FAQ・カテゴリ・タグ |
| `navigation` | クエリ解析・辞書・検索・結果組立 |
| `insight` | 検索ログ・フィードバック・品質評価 |
| `audit` | 監査ログ |

各モジュールの内部は `api / application / domain / infrastructure` で構成する。他モジュールの内部パッケージを直接参照すると `ModularityTest` が失敗する。

## 開発

```bash
# DB を起動（リポジトリのルートで）
make up

# ビルド・テスト（Testcontainers が DB イメージ knowledge-navigator/postgres:local を使う）
./gradlew build

# フォーマット
./gradlew spotlessApply

# 起動（http://localhost:8080/actuator/health）
./gradlew bootRun
```

- テスト用 DB イメージは `make up` でビルドされる。別のイメージを使う場合は `KNAVI_TEST_POSTGRES_IMAGE` を指定する。
- 接続先は環境変数 `DB_URL` / `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` / `POSTGRES_PORT` で変更できる（既定値は `.env.example` と同じ）。
- 形態素解析の辞書（SudachiDict core、約 200MB。ADR-0011）は、`./gradlew test` / `bootRun` の前に Gradle が自動で取り寄せる（`~/.gradle/caches/knavi-sudachi/`。初回だけ約 77MB をダウンロードし、SHA-256 を確かめる）。jar を直接起動するときは `KNAVI_SUDACHI_DICTIONARY` に辞書のファイルかフォルダを指定する。指定がなければ、表記ゆれの吸収なしで検索する。
- モジュール構成図は `./gradlew test` 後に `build/spring-modulith-docs/` に出力される。
