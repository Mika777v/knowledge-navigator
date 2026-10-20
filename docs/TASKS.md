# TASKS — Epic / Story / Task 分解

> ステータス: Draft v0.1 / 2026-10-20
> GitHub上では Epic = 親Issue（`type:epic`）、Story / Task = Sub-issues として登録する。
> Epic 16件と初期 Issue 20件は `scripts/github/create-initial-issues.py` で起票済み（2026-10-20）。
> Size: XS ≤2h / S ≤0.5d / M ≤1d / L ≤3d

---

## 1. Epic 一覧

| ID | Issue | Epic | Phase | Release | 概要 |
|---|---|---|---|---|---|
| E01 | #5 | 開発基盤 | 0 | v0.1.0 | リポジトリ、compose、CI、雛形、ADR |
| E02 | #6 | ダミーデータ・評価セット | 0-1 | v0.1.0 | 架空企業・規程・制度・FAQ・評価質問 |
| E03 | #7 | 認証・権限 | 1 | v0.1.0 | ログイン、ロール、AccessPolicy、監査ログ |
| E04 | #8 | 組織マスタ | 1 | v0.1.0 | 部署・窓口・ユーザー |
| E05 | #9 | 文書管理 | 1 | v0.1.0 | 文書・版・セクション・カテゴリ |
| E06 | #10 | 制度・きっかけ・FAQ | 1 | v0.1.0 | ナビゲーションの中核データ |
| E07 | #11 | ナビゲーション検索 | 1 | v0.1.0 | 正規化・辞書・pg_bigm・スコアリング・窓口導出 |
| E08 | #12 | 利用者UI | 1 | v0.1.0 | ホーム・検索結果・制度/文書詳細 |
| E09 | #13 | 管理UI | 1 | v0.1.0 | コンテンツ・マスタ管理画面 |
| E10 | #14 | 検索ログ・分析・品質評価 | 1 | v0.1.0 | ログ、フィードバック、評価バッチ、CI |
| E11 | #15 | PDF取込・全文検索 | 2 | v0.2.0 | ストレージ、解析、ジョブ、タグ、ビューア |
| E12 | #16 | ナレッジ強化 | 3 | v0.3.0 | 類義語、Sudachi、推薦、サジェスト、ダッシュボード |
| E13 | #17 | AI検索基盤 | 4 | v0.4.0 | ai-service、Embedding、pgvector、ハイブリッド |
| E14 | #18 | RAG | 5 | v0.5.0 | 回答生成、引用検証、SSE、RAG評価 |
| E15 | #19 | AIアシスタント | 6 | v0.6.0 | 確認質問、制度網羅、チェックリスト |
| E16 | #20 | セキュリティ・運用・公開 | 1-6 | 各 | セキュリティスキャン、デモ環境、README、記事 |

---

## 2. Story / Task 分解

### E01 開発基盤

| ID | 種別 | タイトル | Size |
|---|---|---|---|
| E01-S1 | Story | 開発者として `make up` で全サービスを起動したい | — |
| E01-T1 | Task | GitHubリポジトリ作成、ブランチ保護、ラベル、Projects v2 作成 | S |
| E01-T2 | Task | Issue/PRテンプレート、CODEOWNERS、dependabot.yml | S |
| E01-T3 | Task | PostgreSQL 17 + pg_bigm + pgvector の Dockerfile | M |
| E01-T4 | Task | compose.yaml（postgres, backend, frontend）+ Makefile + .env.example | S |
| E01-T5 | Task | Spring Boot 雛形（Gradle KTS, Modulith, Flyway, springdoc, Spotless） | M |
| E01-T6 | Task | Next.js 雛形（TS, Tailwind, shadcn/ui, TanStack Query, ESLint, Vitest） | M |
| E01-T7 | Task | CI: ci-backend / ci-frontend / pr-title | M |
| E01-T8 | Task | OpenAPI → TypeScript 型生成パイプライン | S |
| E01-T9 | Docs | ADR 0001〜0004 | S |

### E02 ダミーデータ・評価セット

| ID | 種別 | タイトル | Size |
|---|---|---|---|
| E02-S1 | Story | デモ閲覧者として、実在しない企業のデータで全機能を試したい | — |
| E02-T1 | Task | 架空商事株式会社の組織（8部署・15窓口・デモユーザー各ロール）YAML | S |
| E02-T2 | Task | 架空規程10本を Markdown で作成（条文構造付き） | L |
| E02-T3 | Task | 制度30件・きっかけ8件・FAQ50件・ナビゲーションキーワード | L |
| E02-T4 | Task | シード投入コマンド（backend の CommandLineRunner / `make seed`） | M |
| E02-T5 | Task | 評価用質問セット50問（Phase3で100問へ） | M |

### E03 認証・権限

| ID | 種別 | タイトル | Size |
|---|---|---|---|
| E03-S1 | Story | 社員として、デモアカウントでログインしたい | — |
| E03-S2 | Story | 情シスとして、公開範囲外のコンテンツが検索にも閲覧にも出ないことを保証したい | — |
| E03-S3 | Story | 人事担当として、自部署所管のコンテンツだけ編集したい | — |
| E03-T1 | Task | Spring Security セッション認証・CSRF・`/me` | M |
| E03-T2 | Task | `AccessPolicy` / `AccessScope`（公開範囲・access_rules・所管部署） | L |
| E03-T3 | Task | 検索SQL用の権限述語生成（プレフィルタ） | M |
| E03-T4 | Task | ロール×公開範囲×操作の権限マトリクステスト（パラメタライズド） | M |
| E03-T5 | Task | 監査ログ（公開・改定・権限変更） | M |

### E04 組織マスタ

| ID | 種別 | タイトル | Size |
|---|---|---|---|
| E04-S1 | Story | 社員として、部署と窓口の一覧・連絡先を見たい | — |
| E04-T1 | Task | departments / contacts / users の Entity・Repository・API | M |
| E04-T2 | Task | 代表窓口（is_primary）制約と総合窓口設定 | S |
| E04-T3 | Task | 部署・窓口一覧画面 | S |

### E05 文書管理

| ID | 種別 | タイトル | Size |
|---|---|---|---|
| E05-S1 | Story | 人事担当として、規程をMarkdownで登録し公開したい | — |
| E05-S2 | Story | 人事担当として、改定時に新しい版を作り施行日を記録したい | — |
| E05-T1 | Task | documents / document_versions / sections / categories / tags スキーマ | M |
| E05-T2 | Task | Markdown → セクション分割（見出し・「第N条」検出、path付与） | L |
| E05-T3 | Task | 文書CRUD・版作成・公開/アーカイブ API（楽観ロック） | L |
| E05-T4 | Task | search_text 正規化（NFKC・見出し連結）ユーティリティ | S |

### E06 制度・きっかけ・FAQ

| ID | 種別 | タイトル | Size |
|---|---|---|---|
| E06-S1 | Story | 人事担当として、制度を登録し根拠条文と窓口を紐づけたい | — |
| E06-S2 | Story | 社員として、きっかけ（結婚等）から使える制度を一覧したい | — |
| E06-S3 | Story | 社員として、FAQを読みたい | — |
| E06-T1 | Task | programs / program_basis / program_contacts と公開時バリデーション | L |
| E06-T2 | Task | life_events / life_event_programs API | M |
| E06-T3 | Task | faqs / faq_links API | M |
| E06-T4 | Task | navigation_keywords API | S |

### E07 ナビゲーション検索

| ID | 種別 | タイトル | Size |
|---|---|---|---|
| E07-S1 | Story | 社員として、自然文で質問して制度・文書・窓口を知りたい | — |
| E07-S2 | Story | 社員として、結果がなくても次に誰に聞けばよいか知りたい | — |
| E07-T1 | Task | クエリ正規化（NFKC・口語ストップワード） | S |
| E07-T2 | Task | 辞書最長一致による語抽出・きっかけ/制度ヒット | M |
| E07-T3 | Task | pg_bigm 全文検索（programs / sections / faqs）+ 権限述語 | L |
| E07-T4 | Task | スコア統合・重み設定化 | M |
| E07-T5 | Task | 結果組立（制度→根拠→文書、窓口導出ルール、関連手続き） | L |
| E07-T6 | Task | `GET /navigate` API・OpenAPI・統合テスト | M |
| E07-T7 | Task | `SearchPort` 抽象化 | S |

### E08 利用者UI

| ID | 種別 | タイトル | Size |
|---|---|---|---|
| E08-T1 | Task | レイアウト・ログイン画面・BFFプロキシ | M |
| E08-T2 | Task | ホーム（検索窓・きっかけ・人気の質問・カテゴリ） | M |
| E08-T3 | Task | 検索結果（4ブロック・ハイライト・フィードバック） | L |
| E08-T4 | Task | 制度詳細 | M |
| E08-T5 | Task | 文書詳細（目次・条文アンカー・版履歴） | L |
| E08-T6 | Task | FAQ一覧・カテゴリ一覧 | M |
| E08-T7 | Task | Playwright E2E（結婚シナリオ・権限シナリオ） | M |

### E09 管理UI

| ID | 種別 | タイトル | Size |
|---|---|---|---|
| E09-T1 | Task | 文書管理（Markdownエディタ・プレビュー・セクション確認・公開） | L |
| E09-T2 | Task | 制度編集（根拠条文ピッカー・窓口・きっかけ） | L |
| E09-T3 | Task | FAQ・キーワード辞書編集 | M |
| E09-T4 | Task | 部署・窓口・ユーザー管理 | M |
| E09-T5 | Task | 監査ログ閲覧 | S |

### E10 検索ログ・分析・品質評価

| ID | 種別 | タイトル | Size |
|---|---|---|---|
| E10-S1 | Story | 人事担当として、ゼロヒットだった質問を見てFAQ改善したい | — |
| E10-S2 | Story | 開発者として、検索ロジック変更で精度が落ちていないかPRで確認したい | — |
| E10-T1 | Task | search_logs / clicks / feedbacks（ユーザー仮名化） | M |
| E10-T2 | Task | 評価バッチ（Hit@k, MRR, 窓口正答率, ゼロヒット率） | M |
| E10-T3 | Task | search-eval workflow（PRコメント投稿） | M |
| E10-T4 | Task | ゼロヒット・人気クエリダッシュボード | M |

### E11 PDF取込・全文検索（Phase2）

| ID | 種別 | タイトル | Size |
|---|---|---|---|
| E11-S1 | Story | 総務担当として、既存のPDF規程をアップロードして検索可能にしたい | — |
| E11-T1 | Task | MinIO・stored_files・アップロードAPI（MIME/サイズ/ハッシュ検証） | M |
| E11-T2 | Task | ダミー規程Markdown → PDF生成スクリプト | S |
| E11-T3 | Task | PDFBox テキスト抽出・ページ保持・条文検出 | L |
| E11-T4 | Task | ingestion_jobs（SKIP LOCKED ワーカー・リトライ・失敗理由） | M |
| E11-T5 | Task | 解析結果確認・セクション修正画面 | L |
| E11-T6 | Task | タグ管理・タグ絞り込み | M |
| E11-T7 | Task | PDFビューア（該当ページを開く） | M |

### E12 ナレッジ強化（Phase3）

| ID | 種別 | タイトル | Size |
|---|---|---|---|
| E12-T1 | Task | Sudachi 正規化形の tsvector 列追加・再インデックスバッチ | L |
| E12-T2 | Task | 類義語辞書（管理画面・クエリ展開） | M |
| E12-T3 | Task | ゼロヒット → 類義語/FAQ追加ワークフロー | M |
| E12-T4 | Task | 重みチューニング（評価セット100問でグリッド探索） | M |
| E12-T5 | Task | 関連制度推薦（きっかけ RELATED・共起クリック） | M |
| E12-T6 | Task | サジェストAPI・UI | M |
| E12-T7 | Spike | OpenSearch（kuromoji）アダプタ比較 | L |

### E13 AI検索基盤（Phase4）

| ID | 種別 | タイトル | Size |
|---|---|---|---|
| E13-T1 | Task | ai-service 雛形（FastAPI, uv, ruff, pytest, CI） | M |
| E13-T2 | Task | LLM/Embedding プロバイダ抽象（Ollama/OpenAI/Azure） | M |
| E13-T3 | Task | チャンク分割・Embedding ジョブ（content_hash 差分） | L |
| E13-T4 | Task | pgvector HNSW・権限フィルタ付きベクトル検索 | M |
| E13-T5 | Task | RRF ハイブリッド統合（HybridSearchAdapter） | M |
| E13-T6 | Task | 類似条文 API・UI | S |
| E13-T7 | Task | 言い換え評価セット・ハイブリッドの効果測定 | M |

### E14 RAG（Phase5）

| ID | 種別 | タイトル | Size |
|---|---|---|---|
| E14-T1 | Task | 文脈構築（引用番号・トークン上限・区切り） | M |
| E14-T2 | Task | 回答生成プロンプト・ストリーミング | M |
| E14-T3 | Task | 引用検証・数値照合・回答不能判定 | L |
| E14-T4 | Task | `/assistant/answers` SSE（backend 中継・窓口はDB値） | M |
| E14-T5 | Task | 回答UI（根拠パネル・免責・フィードバック） | L |
| E14-T6 | Task | RAG評価（引用正確性・忠実性） | L |
| E14-T7 | Task | プロバイダ別送信可能範囲の設定 | S |

### E15 AIアシスタント（Phase6）

| ID | 種別 | タイトル | Size |
|---|---|---|---|
| E15-T1 | Task | 会話セッション・履歴・保持期間 | M |
| E15-T2 | Task | 確認質問（雇用形態等）による制度絞り込み | L |
| E15-T3 | Task | きっかけ起点の制度網羅提示 | M |
| E15-T4 | Task | 申請チェックリスト生成 | M |

### E16 セキュリティ・運用・公開

| ID | 種別 | タイトル | Size |
|---|---|---|---|
| E16-T1 | Task | CodeQL / Trivy / Secret scanning 有効化 | S |
| E16-T2 | Task | release-please・GHCR イメージ発行 | M |
| E16-T3 | Task | デモ環境デプロイ | M |
| E16-T4 | Docs | README（図・GIF・起動方法・設計リンク） | M |
| E16-T5 | Task | セキュリティヘッダ・Markdownサニタイズ | S |

---

## 3. 最初に作成すべき Issue 上位20件

S1〜S3 の着手順。**★ = MVPのクリティカルパス**

| # | Issue | タイトル | Epic | Size | 依存（番号は # 列） |
|---|---|---|---|---|---|
| 1 | #21 | ★ chore: リポジトリ初期設定（ブランチ保護・ラベル・Projects・テンプレート） | E01 | S | — |
| 2 | #22 | ★ docs: 設計ドキュメント一式と ADR 0001〜0004 をコミット | E01 | S | 1 |
| 3 | #23 | ★ build(infra): PostgreSQL 17 + pg_bigm + pgvector の Docker イメージ | E01 | M | 1 |
| 4 | #24 | ★ build: compose.yaml / Makefile / .env.example | E01 | S | 3 |
| 5 | #25 | ★ feat(backend): Spring Boot 雛形（Modulith・Flyway・springdoc・ヘルスチェック） | E01 | M | 4 |
| 6 | #26 | ★ feat(frontend): Next.js 雛形と BFF プロキシ | E01 | M | 4 |
| 7 | #27 | ★ ci: backend / frontend / PRタイトル検証ワークフロー | E01 | M | 5,6 |
| 8 | #28 | ★ feat(seed): 架空商事株式会社の組織・窓口・デモユーザー | E02 | S | — |
| 9 | #29 | ★ feat(seed): 架空規程10本（Markdown・条文構造） | E02 | L | — |
| 10 | #30 | ★ feat(db): 初期スキーマ（組織・文書・制度・FAQ・辞書・ログ） | E05 | M | 5 |
| 11 | #31 | ★ feat(auth): セッション認証・ロール・`/me` | E03 | M | 10 |
| 12 | #32 | ★ feat(content): 文書・版・Markdown セクション分割 | E05 | L | 10 |
| 13 | #33 | ★ feat(content): 制度・きっかけ・FAQ・キーワードの API とシード投入 | E06 | L | 12, 8, 9 |
| 14 | #34 | ★ feat(auth): AccessPolicy と検索用権限述語（プレフィルタ） | E03 | L | 11 |
| 15 | #35 | ★ feat(search): `GET /navigate`（正規化・辞書・pg_bigm・窓口導出） | E07 | L | 13, 14 |
| 16 | #36 | ★ feat(frontend): ホーム・検索結果画面（4ブロック表示） | E08 | L | 15 |
| 17 | #37 | feat(frontend): 文書詳細・制度詳細画面 | E08 | L | 12, 13 |
| 18 | #38 | ★ test(auth): ロール×公開範囲の権限マトリクステスト | E03 | M | 14, 15 |
| 19 | #39 | ★ feat(eval): 評価用質問50問と評価バッチ・CI連携 | E10 | M | 15 |
| 20 | #40 | feat(insight): 検索ログ・クリック・フィードバック | E10 | M | 15 |

> 21件目以降: 管理UI（E09）、監査ログ、README/デモGIF、v0.1.0 リリース。
