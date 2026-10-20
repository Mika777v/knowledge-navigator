# TECH STACK COMPARISON — 社内規程ナビ

> ステータス: Draft v0.1 / 2026-10-20
> 評価: 5 = 非常に良い / 1 = 不利。「学習コスト」は **5 = 低い（学びやすい）**。
> 求人市場価値は日本国内のWeb系・SIer・社内SEの求人傾向に基づく定性評価であり、数値データではない。

---

## 0. 本プロダクトの要件から導いた評価の重み

| 要件 | 技術選定への含意 |
|---|---|
| 日本語の部分一致・表記ゆれ | 日本語検索の作り込み手段があること（最重要） |
| 権限によるフィルタを検索と同時に | 検索とマスタが同一DBにあると有利 |
| データ規模は中小（文書数百〜数千、セクション数万〜数十万） | 専用検索クラスタは過剰になりやすい |
| AIは後から段階導入 | Python エコシステムに接続しやすいこと |
| ポートフォリオとして公開 | `docker compose up` で誰でも動く、外部APIキーなしでも動く |
| 実務適用性（日本企業の社内システム） | Java/Spring・Azure の採用実績が多い |

---

## 1. バックエンド比較

| 観点 | Spring Boot (Java 21) | FastAPI (Python) | NestJS (TypeScript) |
|---|---|---|---|
| 学習コスト | 2（DI・JPA・Securityの概念が多い） | 4（シンプル、型ヒント） | 3（Angular風の構造、デコレータ） |
| 開発速度 | 3 | 5 | 4 |
| 保守性 | 5（静的型、成熟した構造化手法） | 3（規模が大きくなると規律が必要） | 4 |
| AI親和性 | 3（Spring AI 1.x で改善、ただしPythonが先行） | 5（LLM/埋め込み/評価ライブラリが最も豊富） | 3（LangChain.js 等はある） |
| 実務利用実績 | 5（国内の業務システムで最多クラス） | 4（AI・データ系で急増） | 3 |
| 求人市場価値 | 5 | 4（AI関連案件で上昇） | 3 |
| 将来性 | 4 | 5 | 4 |
| コミュニティ規模 | 5 | 4 | 4 |
| ポートフォリオ価値 | 4（「業務系を設計できる」証明） | 4（「AIを組み込める」証明） | 3 |
| 認証・認可の堅さ | 5（Spring Security） | 3（自前組立てが多い） | 4（Passport/Guards） |
| **合計（10項目）** | **41** | **41** | **35** |

**所見**: Spring Boot と FastAPI が同点で、強みが補完関係にある。
→ **コアAPI（認証・認可・ドメイン・検索オーケストレーション）は Spring Boot、AI処理は FastAPI** という役割分担が最も合理的。単一言語にこだわるなら「実務重視 = Spring Boot + Spring AI」「AI重視 = FastAPI のみ」。

---

## 2. フロントエンド比較

| 観点 | Thymeleaf | React (Vite SPA) | Next.js (App Router) |
|---|---|---|---|
| 学習コスト | 5 | 3 | 2（Server/Client Components, キャッシュ挙動） |
| 開発速度 | 4（単純画面）/ 2（インタラクティブ画面） | 4 | 4 |
| 保守性 | 3 | 4 | 4 |
| AI親和性（ストリーミングUI・チャット） | 1 | 4 | 5（ストリーミング、Vercel AI SDK等） |
| 実務利用実績 | 4（社内システム） | 5 | 4 |
| 求人市場価値 | 2 | 5 | 5 |
| 将来性 | 2 | 4 | 5 |
| コミュニティ規模 | 3 | 5 | 5 |
| ポートフォリオ価値 | 2 | 4 | 5 |
| **合計（9項目）** | **26** | **38** | **39** |

**所見**: 社内システムでSEOは不要なため、本来は React SPA でも十分。Next.js を選ぶ理由は
(1) Route Handlers を **BFF** として使い、Cookie・CORS問題を回避できる、(2) RAG のストリーミングUIとの相性、(3) 市場価値。
**SSR依存の機能は最小限にし、SPA的に使う**ことで学習コストを抑える。

---

## 3. 検索基盤比較

| 観点 | PostgreSQL FTS（+ pg_bigm） | Elasticsearch | OpenSearch |
|---|---|---|---|
| 学習コスト | 5（SQLの延長） | 2 | 2 |
| 開発速度 | 5 | 3 | 3 |
| 保守性（運用負荷） | 5（DB1つ） | 2（クラスタ運用・同期処理） | 2 |
| 日本語対応 | 3（標準FTSは非対応、pg_bigm/PGroonga/アプリ側形態素解析で対応） | 5（kuromoji / sudachi プラグイン） | 5（同等） |
| 検索機能の豊富さ | 2 | 5（BM25、ハイライト、集計、同義語） | 5 |
| 権限フィルタとの整合 | 5（JOIN・同一トランザクション） | 3（権限情報の同期が必要） | 3 |
| AI親和性 | 4（pgvector 併用） | 5（ベクトル・ハイブリッド標準） | 5（k-NN・ハイブリッド） |
| 実務利用実績 | 4 | 5 | 4（AWSで多い） |
| 求人市場価値 | 4 | 5 | 4 |
| 将来性 | 4 | 4（ライセンス変遷あり、2024年にAGPL選択肢追加） | 4（Linux Foundation 傘下に移管） |
| コミュニティ規模 | 5 | 5 | 4 |
| ポートフォリオ価値 | 3 | 5 | 4 |
| **合計（12項目）** | **49** | **49** | **45** |

**所見**: 同点だが、**本プロダクトの規模・権限要件・運用コストでは PostgreSQL が明確に有利**。Elasticsearch/OpenSearch の価値（検索機能・日本語解析）は Phase3 以降で効いてくるが、それまでに検索ロジックを `SearchPort` で抽象化しておけば移行は可能。学習目的なら Phase3 で OpenSearch アダプタを「比較実験」として追加するのが最もアピール度が高い。

---

## 4. AI基盤比較

| 観点 | Ollama（ローカルLLM） | OpenAI API | Azure OpenAI |
|---|---|---|---|
| 学習コスト | 4 | 5 | 3（Azureリソース・デプロイ名・認証） |
| 開発速度 | 4 | 5 | 4 |
| 回答品質（日本語） | 3（モデル・GPU依存。Qwen/Gemma系の中規模モデルで実用水準） | 5 | 5（OpenAIモデル同等） |
| コスト | 5（API費用ゼロ、要ハードウェア） | 3（従量） | 3（従量） |
| データ保護（社外秘） | 5（外部送信なし） | 3（API既定では学習利用なし。ただし外部送信） | 5（閉域接続・リージョン選択・企業契約） |
| 実務利用実績（国内企業） | 2 | 3 | 5 |
| 求人市場価値 | 3 | 4 | 5 |
| 将来性 | 4 | 5 | 5 |
| コミュニティ規模 | 4 | 5 | 4 |
| ポートフォリオ価値 | 4（APIキーなしで誰でも試せる） | 3 | 4 |
| **合計（10項目）** | **38** | **41** | **43** |

**所見**: 単独で選ばず **プロバイダ抽象化して併用**。開発・デモ = Ollama、実務想定 = Azure OpenAI、品質比較用 = OpenAI。
Embeddingも同様に `bge-m3`（Ollama） / `text-embedding-3-small`（OpenAI/Azure）を切替可能にする。

---

## 5. ベクトル検索比較

| 観点 | pgvector | ChromaDB | Elasticsearch Vector Search |
|---|---|---|---|
| 学習コスト | 5 | 5 | 2 |
| 開発速度 | 4 | 5 | 3 |
| 保守性 | 5（既存DBに同居） | 3（別ストア・同期が必要） | 3 |
| 権限フィルタ（メタデータ絞り込み） | 5（SQLでJOIN・WHERE） | 3（メタデータフィルタはあるがRDBとの二重管理） | 4 |
| ハイブリッド検索 | 4（pg_bigmとSQLで融合） | 2 | 5 |
| スケール | 3（数百万ベクトルまでは十分） | 3 | 5 |
| 本番運用実績 | 4（主要マネージドPGが対応） | 2（プロトタイプ用途が中心） | 5 |
| 求人市場価値 | 4 | 2 | 4 |
| 将来性 | 5 | 3 | 4 |
| ポートフォリオ価値 | 4 | 2 | 4 |
| **合計（10項目）** | **43** | **30** | **39** |

**所見**: **pgvector が最適**。「権限フィルタ付きベクトル検索」を1つのSQLで書けることが、社内文書RAGで最も重要な性質。ChromaDB はノートブック上の実験に限定して使う。

---

## 6. その他の役割別選定

| 役割 | 推奨 | 代替 | 理由 |
|---|---|---|---|
| DBマイグレーション | Flyway | Liquibase | SQLで書けて学習コスト低 |
| ORM / SQL | Spring Data JPA（CRUD）+ jOOQ or JdbcClient（検索SQL） | MyBatis | 検索SQLは手書きで最適化したい |
| API仕様 | OpenAPI（springdoc）→ openapi-typescript | GraphQL | 生成型でフロントとの不整合防止 |
| UI | Tailwind CSS + shadcn/ui | MUI | 管理画面も含め速く作れる |
| データ取得 | TanStack Query | SWR | キャッシュ・再取得制御 |
| PDF解析 | Phase2: Apache PDFBox（Java）/ Phase4: PyMuPDF + pdfplumber（Python） | Apache Tika, Docling | 日本語PDFは段組み・表で崩れやすく、Pythonの方が選択肢が多い |
| 形態素解析 | Sudachi（Java: sudachi / Python: SudachiPy） | Kuromoji | 正規化形・複数分割単位で表記ゆれに強い |
| ファイル保管 | MinIO（S3互換） | ローカルFS | S3 API でクラウド移行が容易 |
| ジョブキュー | PostgreSQL（SKIP LOCKED）| Redis + Celery / RabbitMQ | 追加ミドル不要 |
| 認証（将来） | Keycloak（デモ）/ Entra ID（実務） | Auth0 | OIDC 標準 |
| テスト | JUnit5 + Testcontainers / Vitest + Playwright / pytest | — | 実DBでの検索テスト必須 |
| CI | GitHub Actions | — | — |
| Python管理 | uv | Poetry | 高速・ロックファイル |
| 可観測性 | OpenTelemetry + 構造化ログ | — | LLM呼び出しのトレースにも利用 |

---

## 7. 目的別 推奨構成

### 7.1 MVP向け（最短で動くものを出す）

| 役割 | 技術 |
|---|---|
| Backend | Spring Boot 4 / Java 21（モジュラーモノリス） |
| Frontend | Next.js（SPA的利用）+ Tailwind + shadcn/ui |
| DB / 検索 | PostgreSQL 17 + pg_bigm |
| AI | なし |
| 起動 | docker compose |

> 代替: 1人で最速を狙うなら **FastAPI + Next.js + PostgreSQL**（2〜3割速い）。ただし Phase4 以降で Python に寄った時に Java を後から足すより、最初から役割分担した方がポートフォリオとしての一貫性がある。

### 7.2 学習効果重視（幅広い技術を体系的に学ぶ）

| 役割 | 技術 |
|---|---|
| Backend | Spring Boot（コア）+ FastAPI（AI） |
| Frontend | Next.js |
| 検索 | PostgreSQL（pg_bigm + Sudachi）**と** OpenSearch（kuromoji）を `SearchPort` 両実装し評価セットで比較 |
| ベクトル | pgvector（本番）+ ChromaDB（実験ノートブック） |
| AI | Ollama（ローカル）+ OpenAI（品質比較） |
| 基盤 | Docker / GitHub Actions / Terraform / OpenTelemetry |

### 7.3 実務重視（日本企業の社内システムに導入する想定）

| 役割 | 技術 |
|---|---|
| Backend | Spring Boot（単一言語運用、AIは Spring AI で Azure OpenAI 呼び出し）|
| Frontend | Next.js（または社内標準があれば React SPA） |
| DB / 検索 | Azure Database for PostgreSQL（pgvector）。日本語全文検索拡張の提供可否を事前確認、不可ならアプリ側形態素解析 + tsvector |
| AI | Azure OpenAI（Private Endpoint） |
| 認証 | Entra ID（OIDC） |
| ストレージ | Azure Blob Storage |
| 実行基盤 | Azure Container Apps / App Service |

### 7.4 AI重視（RAG品質・AI実装力をアピール）

| 役割 | 技術 |
|---|---|
| Backend | FastAPI 単体（または Spring Boot は認可ゲートウェイのみ） |
| Frontend | Next.js + Vercel AI SDK（ストリーミング） |
| 検索 | PostgreSQL + pgvector + pg_bigm（ハイブリッド）+ Cross-Encoder Rerank |
| AI | Ollama / OpenAI / Azure OpenAI 切替、評価に Ragas 等 |
| 取込 | Docling / PyMuPDF、表構造の抽出 |
| 運用 | LLMトレーシング（OpenTelemetry / Langfuse 等）、評価ダッシュボード |

---

## 8. 結論サマリ

| 役割 | 採用 | 次点 |
|---|---|---|
| バックエンド | **Spring Boot**（コア）+ **FastAPI**（AI, Phase4〜） | FastAPI単体 |
| フロントエンド | **Next.js** | React (Vite) |
| 検索基盤 | **PostgreSQL + pg_bigm**（→ Phase3 で Sudachi 併用） | OpenSearch |
| AI基盤 | **Ollama（既定）/ Azure OpenAI（実務）/ OpenAI** を抽象化して切替 | — |
| ベクトル検索 | **pgvector** | Elasticsearch/OpenSearch Vector |

詳細な判断理由は [TECH_STACK_DECISION.md](./TECH_STACK_DECISION.md) を参照。
