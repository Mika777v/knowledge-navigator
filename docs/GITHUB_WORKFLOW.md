# GITHUB WORKFLOW — 社内規程ナビ

> ステータス: Draft v0.1 / 2026-10-20
> リポジトリ: `knowledge-navigator`（Public。ダミーデータのみ）
> 本書は運用ルール（何を）。GitHub への設定方法（どうやって）は [REPOSITORY_SETUP.md](./REPOSITORY_SETUP.md)。

---

## 1. リポジトリ初期設定

| 設定 | 値 |
|---|---|
| Visibility | Public（ポートフォリオ） |
| Default branch | `main` |
| License | MIT（コード）/ ダミーデータは CC BY 4.0 を README に明記 |
| Merge方式 | **Squash merge のみ許可**（PRタイトル = コミットメッセージ） |
| Auto-delete head branches | ON |
| Branch protection (`main`) | PR必須、CI必須（backend / frontend / lint / eval）、直接push禁止、linear history、会話の解決必須 |
| Security | Dependabot alerts & updates、Secret scanning、Push protection、CodeQL |
| Topics | `rag`, `spring-boot`, `nextjs`, `fastapi`, `pgvector`, `postgresql`, `japanese-search`, `knowledge-management` |

> 1人開発でもPRを必ず作る（設計判断の記録・CI結果・スクリーンショットがポートフォリオになる）。セルフレビューのチェックリストをPRテンプレートに含める。

---

## 2. Branch 戦略: GitHub Flow + タグリリース

```
main ─────●────────●──────────●──────── (常にデプロイ可能)
           \      / \        /
            feat/12-navigate-api
                     fix/31-contact-fallback
```

| ブランチ | 用途 | 命名 |
|---|---|---|
| `main` | 常に動く状態。リリースはタグで切る | — |
| `feat/<issue>-<slug>` | 機能追加 | `feat/12-navigate-api` |
| `fix/<issue>-<slug>` | バグ修正 | `fix/31-contact-fallback` |
| `docs/<issue>-<slug>` | ドキュメント | `docs/5-adr-pg-bigm` |
| `chore/` `refactor/` `test/` `ci/` | その他 | `ci/8-eval-workflow` |
| `spike/<slug>` | 技術検証（マージしない／結果はADRかIssueに記録） | `spike/opensearch-kuromoji` |

- ブランチ寿命は **最大3日** を目安。大きい機能は feature flag（設定値）で未完成部分を隠してマージする。
- git-flow（develop/release ブランチ）は採用しない（1人〜少人数で過剰）。

---

## 3. Commit 規約: Conventional Commits

```
<type>(<scope>): <subject>

<body>  # 何を・なぜ（どうやって はコードを見ればわかる）

Refs #12
```

| type | 用途 |
|---|---|
| `feat` | 機能追加（minor） |
| `fix` | バグ修正（patch） |
| `docs` | ドキュメント |
| `refactor` | 振る舞いを変えない変更 |
| `perf` | 性能改善 |
| `test` | テスト |
| `build` / `ci` | ビルド・CI |
| `chore` | 雑務 |
| `search` | **検索品質に影響する変更**（重み・辞書・正規化）。評価結果をbodyに記載 |

scope: `backend`, `frontend`, `content`（文書・制度・FAQ）, `insight`（検索ログ・分析）, `eval`（検索品質評価）, `ai`, `ingest`, `db`, `search`, `auth`, `infra`, `docs`, `seed`, `deps`

例:
```
feat(search): きっかけ辞書による制度の直接ヒットを追加

「結婚」「出産」等のライフイベント語を navigation_keywords で制度へ直接マッピング。
評価セット Hit@5: 58% → 72%

Refs #14
```

- 破壊的変更は `feat(api)!:` + `BREAKING CHANGE:` フッタ
- commitlint を CI で検証（PRタイトルを検証すれば squash 後も守られる）

---

## 4. Pull Request 運用

| ルール | 内容 |
|---|---|
| サイズ | 差分 400行以内目安（生成コード・シード除く） |
| タイトル | Conventional Commits 形式（squash後のコミットになる） |
| 紐づけ | 本文に `Closes #<issue>` |
| 必須CI | build, test, lint, type-check, openapi-diff, search-eval（検索系変更時） |
| UI変更 | スクリーンショット / GIF 必須 |
| 検索品質変更 | 評価結果（Before/After）必須。eval workflow がPRコメントで自動投稿 |
| DBマイグレーション | 後方互換性の確認（Expand → Migrate → Contract） |
| レビュー | CODEOWNERS。1人開発時はセルフレビュー + 翌日見直し（AIレビューを補助に使っても可） |
| Draft PR | 早めに作り、CIを回しながら進める |

### 4.1 Pull Request テンプレート（`.github/pull_request_template.md`）

```markdown
## 概要
<!-- 何を・なぜ -->

Closes #

## 変更内容
-

## スクリーンショット（UI変更時）

## 検索品質への影響（search系変更時）
| 指標 | Before | After |
|---|---|---|
| Hit@5 | | |
| MRR | | |
| ゼロヒット率 | | |

## チェックリスト
- [ ] テストを追加/更新した
- [ ] 権限（公開範囲・所管部署）に影響する変更の場合、権限マトリクステストを更新した
- [ ] DBマイグレーションは後方互換である
- [ ] OpenAPI を更新した（API変更時）
- [ ] 実在の社内文書・個人情報・秘密情報を含んでいない
- [ ] ドキュメント（docs/ / ADR）を更新した
```

---

## 5. Issue テンプレート（`.github/ISSUE_TEMPLATE/`）

### 5.1 `story.yml`（ユーザーストーリー）

```yaml
name: Story
description: 利用者価値のある機能単位
title: "[Story] "
labels: ["type:story"]
body:
  - type: input
    id: epic
    attributes: { label: Epic, placeholder: "#3 ナビゲーション検索" }
  - type: textarea
    id: story
    attributes:
      label: ユーザーストーリー
      value: |
        **<ロール>** として、**<やりたいこと>** をしたい。
        なぜなら **<目的>** だから。
    validations: { required: true }
  - type: textarea
    id: ac
    attributes:
      label: 受け入れ条件
      value: |
        - [ ] Given ... When ... Then ...
    validations: { required: true }
  - type: dropdown
    id: phase
    attributes: { label: Phase, options: ["0","1","2","3","4","5","6"] }
  - type: dropdown
    id: size
    attributes: { label: 見積り, options: ["XS (≤2h)","S (≤0.5d)","M (≤1d)","L (≤3d)","XL (要分割)"] }
```

### 5.2 `task.yml`（技術タスク）、`bug.yml`、`spike.yml`、`search-quality.yml`

| テンプレート | 主な項目 |
|---|---|
| `task.yml` | 親Story、作業内容、完了条件（DoD） |
| `bug.yml` | 再現手順、期待結果、実際の結果、環境、ログ |
| `spike.yml` | 問い（何を判断したいか）、タイムボックス、判断基準、成果物（ADR） |
| `search-quality.yml` | クエリ、期待した結果、実際の結果、原因仮説（辞書不足/正規化/重み） → 評価セットに追加 |
| `config.yml` | `blank_issues_enabled: false` |

### 5.3 Definition of Done

- 受け入れ条件を満たす / テストが通る / CIグリーン / ドキュメント更新 / デモデータで動作確認

---

## 6. ラベル設計

| 系統 | ラベル |
|---|---|
| type | `type:epic` `type:story` `type:task` `type:bug` `type:spike` `type:docs` |
| area | `area:backend` `area:frontend` `area:ai` `area:ingest` `area:db` `area:search` `area:infra` `area:auth` |
| phase | `phase:0` 〜 `phase:6` |
| priority | `P0`（ブロッカー） `P1`（MVP必須） `P2`（あると良い） `P3` |
| status補助 | `blocked` `needs-design` `good first issue` |

ラベルは `.github/labels.yml` で管理し、`github-label-sync` 等で同期する。

---

## 7. Release 運用

| 項目 | 方針 |
|---|---|
| バージョニング | SemVer。`0.x` の間は minor = フェーズ到達 |
| タグ | `v0.1.0`（MVP）, `v0.2.0`（Phase2）, … `v1.0.0`（RAG + アシスタント公開） |
| 自動化 | **release-please**（`release-please.yml`）が Conventional Commits から次の版を決め、リリースの準備の PR（`chore: release x.y.z`）を作る → オーナーがマージするとタグ・GitHub Release を作る（下の §7.1）。CHANGELOG.md は手で書く（release-please には書かせない） |
| 成果物 | GitHub Release にリリースノート、スクリーンショット、評価レポート（Hit@5 推移）を添付 |
| コンテナ | release-please がタグを作ったら、同じワークフローから `release-images.yml` を呼び、GHCR に `backend` / `frontend` / `postgres` / `ai-service` イメージを発行する（手でタグを push したときも動く） |
| デモ環境 | Release 公開時にデモ環境へデプロイ（手動承認付き environment） |

### 7.1 リリースの手順（release-please）

開発環境（Claude Code のセッション）からはタグの push と Release の作成ができない。タグと Release は、オーナーがリリースの準備の PR をマージしたときに GitHub Actions が作る（CLAUDE.md の PR の進め方 7）。

はじめに一度だけ: リポジトリの「Settings」→「Actions」→「General」→ いちばん下の「Workflow permissions」で、**「Allow GitHub Actions to create and approve pull requests」にチェック**を入れて「Save」。これが無いと release-please が PR を作れない。

1. main に PR がマージされるたびに、release-please が**リリースの準備の PR**（タイトル `chore: release x.y.z`）を作る・更新する。版の番号は、前の版からの PR のタイトルで決まる（`feat` があれば minor、`fix` だけなら patch。`0.x` の間は破壊的な変更でも minor）。この PR は `.release-please-manifest.json`・`frontend/package.json`・`backend/build.gradle.kts` の版の番号だけを変える
2. Claude が **CHANGELOG の版の節と README の更新の PR**（例: #195）を作る。自動マージは予約しない。オーナーが確かめてマージする
3. オーナーがリリースの準備の PR をマージする。この PR は GitHub Actions が作ったため CI が動かず、必須のチェックが付かない。**PR の下の「Merge without waiting for requirements to be met (bypass rules)」にチェックを入れて**「Squash and merge」する（管理者の権限）
4. マージすると、`release-please.yml` がタグ（`vX.Y.Z`）と GitHub Release を作り、続けて GHCR にイメージを公開する。Actions の画面で成功を確かめる（GHCR のパッケージは非公開のまま。公開する場合はパッケージの設定で変える）
5. Release の本文は PR のタイトルの一覧から自動で作られる。CHANGELOG の版の節に差し替えたいときは、Release の画面の「Edit」で、Claude が渡すリリースノートを貼る。タイトルは `vX.Y.Z（版の名前）`（例: `v0.3.0（検索のナレッジ強化）`）に直す
6. 新しいセッションで次の版を始める（CLAUDE.md の「トークンの使い方」）

release-please が動かないときは、これまでどおり GitHub の画面の「Releases」→「Draft a new release」から手でタグと Release を作れる（タグの push で `release-images.yml` が動く）。

| バージョン | 内容 |
|---|---|
| v0.1.0 | Phase1 MVP |
| v0.2.0 | Phase2 PDF取込・全文検索 |
| v0.3.0 | Phase3 類義語・推薦 |
| v0.4.0 | Phase4 ハイブリッド検索 |
| v0.5.0 | Phase5 RAG |
| v0.6.0 / v1.0.0 | Phase6 AIアシスタント |

---

## 8. GitHub Actions

### 8.1 構成: ゲート + 再利用ワークフロー

```
PR / main への push
  └─ ci.yml（ゲート）
       ├─ changes        … 変更されたファイルを判定（dorny/paths-filter）
       ├─ backend  ──▶ ci-backend.yml   （backend/・DB イメージが変わったとき）
       ├─ frontend ──▶ ci-frontend.yml  （frontend/ が変わったとき）
       ├─ db       ──▶ ci-db.yml        （infra/docker/postgres/ が変わったとき）
       ├─ compose  ──▶ ci-compose.yml   （compose・Makefile・各サービスが変わったとき）
       ├─ data     ──▶ ci-data.yml      （data/・scripts/data/ が変わったとき）
       └─ ci-ok    … 上記の結果を集約（スキップは成功扱い）← 必須チェック
```

各 CI にパスフィルターを直接書いて必須チェックにすると、関係ない PR ではチェックが実行されず「待機中」のままマージできなくなる。
そのため、常に実行されるゲートジョブ `ci-ok` だけを必須にしている。

### 8.2 ワークフロー一覧

| Workflow | トリガー | 内容 |
|---|---|---|
| `ci.yml` | 全 PR / push(main) | 変更判定 → 各 CI を呼ぶ → `ci-ok` で集約（**必須**） |
| `pr-title.yml` | PR | PR タイトルの Conventional Commits 検証（`conventional-commits` が**必須**） |
| `ci-backend.yml` | ゲートから / 手動 | DB イメージのビルド → Gradle build・test（Testcontainers）→ コンテナイメージのビルド |
| `ci-frontend.yml` | ゲートから / 手動 | lint / typecheck / test / build → コンテナイメージのビルド |
| `ci-db.yml` | ゲートから / 手動 | pg_bigm のチェックサム検証 → DB イメージのスモークテスト |
| `ci-compose.yml` | ゲートから / 手動 | `make up` → DB 設定・backend・frontend（BFF 経由）の疎通確認 |
| `ci-data.yml` | ゲートから / 手動 | ダミーデータ（seed・規程）の整合性検査 |
| `ci-terraform.yml` | ゲートから / 手動 | デモ環境の AWS 構成（`infra/terraform/demo`）の書式・文法・セキュリティ設定の検査（AWS には接続しない） |
| `labels.yml` | push(main) で labels.yml 変更時 | ラベル同期 |
| `search-eval.yml` | （#39 で追加） | 評価セット実行 → PR に結果コメント |
| `release-please.yml` | push(main) | リリースの準備の PR を作る・更新する。マージされたらタグ・Release を作り、`release-images.yml` を呼ぶ |
| `codeql.yml` / `trivy.yml` | （E16 で追加） | セキュリティスキャン |

新しい CI を追加するときは、`workflow_call` で再利用ワークフローとして作り、`ci.yml` の `changes` と `ci-ok` の `needs` に加える。

## 9. GitHub Projects 運用

### 9.1 プロジェクト: 「Knowledge Navigator Roadmap」（Projects v2）

**カスタムフィールド**

| フィールド | 型 | 値 |
|---|---|---|
| Status | Single select | `Backlog` / `Ready` / `In Progress` / `In Review` / `Done` |
| Phase | Single select | `P0 Setup` `P1 MVP` `P2 Search+` `P3 Knowledge` `P4 Vector` `P5 RAG` `P6 Assistant` |
| Epic | Single select（またはSub-issues の親で代替） | E01〜E14 |
| Priority | Single select | P0〜P3 |
| Size | Single select | XS / S / M / L / XL |
| Iteration | Iteration | 2週間スプリント（Sprint 1 = 2026-11-02〜） |
| Target Release | Single select | v0.1.0 〜 v1.0.0 |
| Area | Single select | backend / frontend / ai / ingest / db / search / infra / docs |

**ビュー**

| ビュー | 種類 | 用途 |
|---|---|---|
| 📋 Board | Board（Status列） | 日々の作業 |
| 🏃 Current Sprint | Table（Iteration = @current） | スプリント管理 |
| 🗺 Roadmap | Roadmap（Iteration / Target Release 軸） | 6か月計画の可視化（ポートフォリオに載せる） |
| 🧩 By Epic | Table（Group by Epic） | 進捗の俯瞰 |
| 🔍 Search Quality | Table（label: area:search） | 検索品質課題 |
| 🐛 Bugs | Table（type:bug） | — |

**自動化（Workflows）**
- Issue 追加 → Status: `Backlog`
- PR オープン → 紐づくIssueを `In Review`
- PR マージ / Issue クローズ → `Done`
- Sub-issues（GitHub標準）で Epic → Story → Task の親子関係を管理し、Epic の進捗率を表示

### 9.2 運用リズム

| 頻度 | 活動 |
|---|---|
| 毎日 | Board 更新、ブランチ ≤ 3日 |
| 2週間 | スプリント計画（Ready から選択）/ ふりかえり（`docs/sprints/` に短い記録） |
| フェーズ完了 | Release、評価レポート、README のスクリーンショット更新、ブログ/Zenn記事化 |

### 9.3 Milestones

Projects とは別に、GitHub Milestones を Release と対応づける: `v0.1.0 MVP`, `v0.2.0 PDF`, …（期日付き）
