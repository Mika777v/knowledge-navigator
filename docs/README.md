# 社内規程ナビ（Knowledge Navigator）— 設計ドキュメント

> 「何を調べればいいかわからない」を、「誰に・何を・どこで」まで案内する。
> ※ 本プロジェクトのデータはすべて架空企業「架空商事株式会社」のダミーデータです。

## ドキュメント一覧

| ドキュメント | 内容 |
|---|---|
| [PRODUCT_VISION.md](./PRODUCT_VISION.md) | ビジョン、課題、ペルソナ、原則、KPI |
| [PRD_KNOWLEDGE_NAVIGATOR.md](./PRD_KNOWLEDGE_NAVIGATOR.md) | 機能要件（Phase1-6）、MVP定義、ユースケース、画面設計、権限設計、非機能要件 |
| [DOMAIN_MODEL.md](./DOMAIN_MODEL.md) | ドメインモデル、エンティティ、DB設計（DDL） |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | システム構成、API設計、検索アーキテクチャ、AI導入アーキテクチャ、セキュリティ、ディレクトリ構成 |
| [TECH_STACK_COMPARISON.md](./TECH_STACK_COMPARISON.md) | 技術比較表、目的別推奨構成 |
| [TECH_STACK_DECISION.md](./TECH_STACK_DECISION.md) | 採用技術と判断理由、ADR一覧 |
| [GITHUB_WORKFLOW.md](./GITHUB_WORKFLOW.md) | ブランチ・コミット・PR・リリース・Issue/PRテンプレート・Projects |
| [REPOSITORY_SETUP.md](./REPOSITORY_SETUP.md) | 上記ルールを GitHub に設定する方法（ファイル・スクリプト・手動手順） |
| [ROADMAP.md](./ROADMAP.md) | 6か月ロードマップ、スプリント計画 |
| [TASKS.md](./TASKS.md) | Epic / Story / Task 分解、初期Issue 20件 |
| [DESIGN_REVIEW.md](./DESIGN_REVIEW.md) | 設計レビュー、アーキテクトとしての提言 |

---

## 最終まとめ

### 1. 推奨システム名

**社内規程ナビ（英名: Knowledge Navigator / 略称 KNavi）**
— 「検索」ではなく「案内（ナビ）」であることを名前で示す。

### 2. 推奨リポジトリ名

| 候補 | 評価 |
|---|---|
| **`knowledge-navigator`** | ◎ **推奨**。コンセプト（ナビゲーション）と対象範囲（規程に限らずマニュアル・FAQ・組織）を両方表現。PRD名と一致 |
| `policy-navigator` | ○ 規程に特化した印象。マニュアル・組織図まで含むと狭い |
| `kitei-navi` | ○ 日本語らしく覚えやすいが、海外の閲覧者に意味が伝わらない |
| `company-knowledge-hub` | △ 「Hub（集約）」で、案内のコンセプトが弱い |
| `corporate-knowledge-search` | △ 「単なる検索ではない」というコンセプトと逆 |
| `employee-knowledge-assistant` | △ AIアシスタントの印象が先行し、MVPの姿と合わない |
| `internal-rules-guide` | △ 汎用的すぎる |

### 3. 推奨ディレクトリ構成

MVP: `backend/` `frontend/` `data/{seed,eval}/` `infra/docker/` `docs/{adr,api}/` `.github/`
将来: + `ai-service/` `ingest-worker/` `packages/api-client/` `infra/terraform/` `scripts/`
（詳細: [ARCHITECTURE.md §8](./ARCHITECTURE.md)）

### 4. 推奨技術構成

Next.js（BFF）/ Spring Boot 3・Java 21（モジュラーモノリス）/ PostgreSQL 17（pg_bigm → Sudachi → pgvector）/ FastAPI ai-service（Phase4〜）/ Ollama・Azure OpenAI・OpenAI 切替 / Docker Compose / GitHub Actions

### 5. 技術比較結果

| 役割 | 採用 | 決め手 |
|---|---|---|
| Backend | Spring Boot（コア）+ FastAPI（AI） | 認可・ドメインの堅さ ＋ AIエコシステム |
| Frontend | Next.js | BFF・ストリーミングUI・市場価値 |
| 検索 | PostgreSQL + pg_bigm | 権限プレフィルタを1SQLで、運用1つ |
| AI | Ollama 既定 / Azure OpenAI 実務 | APIキー不要のデモ ＋ 閉域の実務説明 |
| ベクトル | pgvector | 権限フィルタ付きベクトル検索、ハイブリッド容易 |

### 6. MVPで採用する技術

Next.js / TypeScript / Tailwind / shadcn/ui / TanStack Query、Spring Boot / Spring Security / Spring Modulith / Flyway / springdoc、PostgreSQL 17 + pg_bigm、JUnit5 + Testcontainers / Vitest / Playwright、Docker Compose、GitHub Actions。**AI関連は含めない。**

### 7. 将来のRAG拡張方針

1. セクション（条文）を検索・引用・Embedding の共通単位として Phase1 から持つ
2. Phase4 で pgvector + RRF ハイブリッド（`SearchPort` 差し替え）
3. Phase5 で RAG。**権限は backend がスコープを確定 → ai-service はスコープ内のみ検索**
4. **窓口・根拠・適用条件は構造化データから描画し、LLMは説明文に限定**
5. 引用検証・回答不能判定・評価セットで品質を数値管理
6. LLMプロバイダは抽象化し、公開範囲ごとに送信可否を制御

### 8. MVP完成までのロードマップ（8週間）

| Sprint | 期間 | ゴール |
|---|---|---|
| S1 | 11/02〜11/15 | 開発基盤・ダミーデータ・初期スキーマ・ADR |
| S2 | 11/16〜11/29 | 認証・組織マスタ・文書/版/セクション |
| S3 | 11/30〜12/13 | 制度・きっかけ・FAQ・`/navigate`・検索結果画面 |
| S4 | 12/14〜12/27 | 権限マトリクス・管理画面・ログ・評価CI → **v0.1.0** |

### 9. GitHub Projects 初期構成

- プロジェクト「Knowledge Navigator Roadmap」
- フィールド: Status / Phase / Epic / Priority / Size / Iteration（2週） / Target Release / Area
- ビュー: Board、Current Sprint、Roadmap、By Epic、Search Quality、Bugs
- 自動化: 追加→Backlog、PR→In Review、マージ→Done、Sub-issues で Epic 進捗

### 10. Epic 一覧

E01 開発基盤 / E02 ダミーデータ・評価セット / E03 認証・権限 / E04 組織マスタ / E05 文書管理 / E06 制度・きっかけ・FAQ / E07 ナビゲーション検索 / E08 利用者UI / E09 管理UI / E10 検索ログ・分析・品質評価 / E11 PDF取込・全文検索 / E12 ナレッジ強化 / E13 AI検索基盤 / E14 RAG / E15 AIアシスタント / E16 セキュリティ・運用・公開

### 11. 最初に作成すべき Issue 上位20件

[TASKS.md §3](./TASKS.md) を参照（リポジトリ初期設定 → DB/compose → 雛形 → CI → ダミーデータ → スキーマ → 認証 → 文書/セクション → 制度/FAQ → AccessPolicy → `/navigate` → 画面 → 権限テスト → 評価 → ログ）。

### 12. ポートフォリオとしてアピールできるポイント

1. **課題設定力**: 「検索」ではなく「ナビゲーション」として再定義し、制度・窓口・きっかけをドメインモデルの中心に据えた
2. **AIありきでない段階設計**: AIなしで成立するMVP → 意味検索 → RAG と、各段階の価値を説明できる
3. **評価駆動の検索改善**: 評価用質問セットをCIで回し、Hit@5 の推移（例: 58% → 85% → 90%）で改善を数値化
4. **日本語検索の実践**: PostgreSQL標準FTSの限界を理解し、pg_bigm・Sudachi・ベクトルを段階的に導入
5. **権限を守るRAG**: プレフィルタ、backend によるスコープ確定、窓口・根拠をLLMに生成させない設計
6. **実務を意識した設計**: 所管部署による編集権限、版管理と施行日、監査ログ、職務分離、社外秘文書の扱い
7. **ポリグロットの役割分担**: Java（業務・認可）と Python（AI）を責務で分けた理由を ADR で説明
8. **外部APIキー不要で動くデモ**: Ollama でローカル完結、`docker compose up` で誰でも試せる
9. **GitHub運用**: Conventional Commits、release-please、Projects ロードマップ、Issue/PRテンプレート、ADR
10. **ドキュメント**: Vision → PRD → ドメイン → アーキテクチャ → 技術比較 → ロードマップまで一貫した設計資料
