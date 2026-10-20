# TECH STACK DECISION — 社内規程ナビ

> ステータス: Accepted (v0.1) / 2026-10-20
> 比較表は [TECH_STACK_COMPARISON.md](./TECH_STACK_COMPARISON.md)。個別判断は `docs/adr/` に ADR として記録する。

---

## 1. 決定した構成

| 役割 | 技術 | 導入Phase |
|---|---|---|
| Frontend | Next.js 15系（App Router, TypeScript）+ Tailwind CSS + shadcn/ui + TanStack Query | 1 |
| BFF | Next.js Route Handlers（`/api/*` を backend へプロキシ、SSE中継） | 1 |
| Backend | Spring Boot 3.x / Java 21 / Gradle (Kotlin DSL) / Spring Security / Spring Modulith | 1 |
| DB | PostgreSQL 17 | 1 |
| 日本語検索 | pg_bigm（Phase1）→ + Sudachi 正規化 tsvector（Phase3） | 1 / 3 |
| マイグレーション | Flyway | 1 |
| API仕様 | OpenAPI 3.1（springdoc）→ openapi-typescript | 1 |
| ファイル保管 | MinIO（S3互換） | 2 |
| PDF解析 | Apache PDFBox（Phase2）→ Python（PyMuPDF/pdfplumber）へ移管（Phase4） | 2 / 4 |
| AI Service | FastAPI / Python 3.12 / uv | 4 |
| ベクトル | pgvector（HNSW） | 4 |
| LLM / Embedding | Ollama（既定: bge-m3 / Qwen系 or Gemma系）, OpenAI, Azure OpenAI（切替式） | 4-5 |
| テスト | JUnit 5 + Testcontainers, Vitest + Testing Library, Playwright, pytest | 1〜 |
| CI/CD | GitHub Actions, Dependabot, CodeQL, Trivy | 1 |
| 実行 | Docker Compose（ローカル/デモ） | 1 |

> 各ライブラリの具体的なバージョンは実装開始時点の最新安定版（LTS）を採用し、`gradle/libs.versions.toml`・`package.json`・`pyproject.toml` で固定する。

---

## 2. 判断理由

### D-01: バックエンドのコアは Spring Boot

- 本プロダクトの複雑さの中心は **AI ではなく認可・ドメインルール**（所管部署による編集制限、公開範囲、版管理、窓口導出）。ここは静的型と Spring Security の成熟度が効く。
- 国内の社内システム・SIer案件での採用実績と求人数が最も多く、「実務で通用する設計」の証明になる。
- Spring Modulith により、モジュラーモノリスの境界をテストで検証でき、設計力のアピールになる。
- **却下理由（FastAPI単体）**: 開発速度は上だが、認可・トランザクション・監査の作り込みで自前実装が増える。AI部分のみに使う方が両者の強みが出る。
- **却下理由（NestJS）**: フロントと言語統一できる利点はあるが、AI親和性・国内業務系の実績ともに中間で決め手がない。

### D-02: AI処理は FastAPI の別サービス（Phase4〜）

- Embedding、PDF解析、チャンク分割、RAG評価のライブラリは Python が最も充実。
- Phase1〜3 は不要なため、**MVPの複雑さを増やさない**（必要になってから追加）。
- 認可は backend が担い、ai-service は許可済みスコープを受け取るだけ（認可ロジックの二重実装を防止）。
- 代替: 単一言語を優先する組織では Spring AI で実装可能（実務重視構成）。`assistant` モジュールの `AiClient` インターフェース越しに呼ぶため差し替え可能。

### D-03: フロントエンドは Next.js

- BFF（Route Handlers）で backend へのプロキシとSSE中継をまとめられ、Cookie ベース認証を同一オリジンで扱える。
- Phase5-6 のストリーミング回答UIとの親和性。
- SEO不要の社内システムなので **Server Components の高度な機能（ISR、Server Actions 多用）は使わない**方針で学習コストと複雑さを抑える。

### D-04: 検索は PostgreSQL + pg_bigm から始める

- データ規模（セクション数万〜数十万）に対して専用検索クラスタは過剰。
- **権限フィルタを検索SQLの WHERE で強制できる**ことが社内文書検索の最重要要件。別ストアへ同期すると権限変更の反映遅延が情報漏えいリスクになる。
- PostgreSQL 標準 FTS は日本語を分かち書きしないため、**pg_bigm（部分一致の取りこぼしが少ない）** を採用。ランキング改善と表記ゆれは Phase3 で Sudachi 正規化を追加。
- `SearchPort` を抽象化し、OpenSearch への移行パスを確保（移行基準は ARCHITECTURE.md 4.5）。

### D-05: ベクトルは pgvector

- 同上の理由で「権限フィルタ付きベクトル検索」を1SQLで書ける。
- キーワード結果とのハイブリッド（RRF）もSQL/アプリで完結。
- ChromaDB は実験用途に限定（本番ストアにしない）。

### D-06: LLM はプロバイダ抽象化、既定は Ollama

- ポートフォリオ閲覧者が **APIキーなしで `docker compose --profile ai up` するだけで試せる**。
- 「社外秘文書を外部に送らない構成」を実際に示せる。
- 実務想定として Azure OpenAI 実装を用意し、閉域・データ所在地の設計を ADR で説明する。
- 公開範囲ごとに送信可能なプロバイダを設定できるようにする（例: CONFIDENTIAL はどのLLMにも送らない）。

### D-07: AIフレームワークへの全面依存を避ける

- LangChain / LlamaIndex は変化が速く、抽象が厚い。RAGのコア（検索 → 文脈構築 → 生成 → 引用検証）は数百行で自前実装でき、**仕組みを理解していることのアピール**にもなる。
- 必要に応じて部分的に利用（テキスト分割、評価など）。

### D-08: ジョブキューは PostgreSQL

- 取込・Embedding のジョブ量は小さい。`SELECT ... FOR UPDATE SKIP LOCKED` で十分。
- Redis/RabbitMQ は運用対象を増やすだけ。規模が増えたら移行。

---

## 3. 技術を採用しない判断（意図的に使わないもの）

| 技術 | 理由 |
|---|---|
| マイクロサービス（最初から） | 1人開発・小規模で分散の複雑さに見合わない |
| Kubernetes | デモ用途では compose / コンテナPaaSで十分。将来オプション |
| GraphQL | 画面とAPIが素直に対応しており REST + OpenAPI で十分 |
| Elasticsearch（MVP） | 運用・同期コスト > 得られる検索品質（Phase3以降に比較実験として検討） |
| ChromaDB（本番） | 権限・整合性の二重管理 |

---

## 4. ADR 初期リスト

> 作成済みの ADR と予定は [adr/README.md](./adr/README.md) を参照。

| No | タイトル |
|---|---|
| 0001 | モジュラーモノリス（Spring Boot）を採用する |
| 0002 | 日本語検索に pg_bigm を採用する |
| 0003 | 制度（Program）を文書から独立したエンティティにする |
| 0004 | 権限フィルタは検索前（プレフィルタ）で適用する |
| 0005 | Next.js を BFF として使う |
| 0006 | AI処理を FastAPI 別サービスに分離する（Phase4） |
| 0007 | ベクトルストアに pgvector を採用する |
| 0008 | LLMプロバイダを抽象化し既定を Ollama とする |
| 0009 | AI回答の窓口・根拠表示はLLM生成ではなくDB値から描画する |
