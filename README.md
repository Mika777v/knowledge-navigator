# 社内規程ナビ（Knowledge Navigator）

> 「何を調べればいいかわからない」を、「誰に・何を・どこで」まで案内する。

社内規程・福利厚生・手順書・FAQ・組織情報を横断し、自然言語の質問から
**関連制度 / 関連文書 / 該当箇所 / 問い合わせ先 / 担当部署** を案内するナビゲーションシステムです。

> ⚠️ 本リポジトリのデータはすべて架空企業「架空商事株式会社」のダミーデータです。実在の企業・規程・人物とは関係ありません。

## ステータス

設計フェーズ（Phase0）。実装は [ROADMAP](docs/ROADMAP.md) に沿って進めます。

## ローカルで動かす

必要なもの: Docker（Compose v2）、make。`make test` には Java 21 と Node.js 22 + pnpm も必要

```bash
make up      # .env が無ければ .env.example から作成し、ビルドして起動
make psql    # DB に接続
make down    # 停止（データは残る）
make clean   # 停止してデータも削除
make test    # テスト（backend・frontend）
make help    # コマンド一覧
```

| サービス | URL |
|---|---|
| frontend | http://localhost:3000 |
| backend | http://localhost:8080/actuator/health 、API 仕様 http://localhost:8080/swagger-ui.html |
| PostgreSQL | localhost:5432（pg_bigm / pgvector 入り） |

## ドキュメント

- [設計ドキュメント一覧・最終まとめ](docs/README.md)
- [Product Vision](docs/PRODUCT_VISION.md) / [PRD](docs/PRD_KNOWLEDGE_NAVIGATOR.md) / [Architecture](docs/ARCHITECTURE.md)
- [GitHub 運用ルール](docs/GITHUB_WORKFLOW.md) / [リポジトリ初期設定の手順](docs/REPOSITORY_SETUP.md)

## 予定技術スタック

Next.js / Spring Boot 4 (Java 21) / PostgreSQL 17 (pg_bigm, pgvector) / FastAPI / Ollama・Azure OpenAI・OpenAI

## ライセンス

コード: [MIT](LICENSE)。ダミーデータ（`data/`）: CC BY 4.0。
