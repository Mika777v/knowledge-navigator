# REPOSITORY SETUP — リポジトリ初期設定の実現方法

> ステータス: v0.1 / 2026-10-20
> 対応 Issue: TASKS.md #1「リポジトリ初期設定（ブランチ保護・ラベル・Projects・テンプレート）」
> **何を** 設定するか（運用ルール）は [GITHUB_WORKFLOW.md](./GITHUB_WORKFLOW.md)、
> **どうやって** 設定するか（手順・仕組み）はこのドキュメント。

---

## 1. 全体像: 設定を「コード」で管理する

GitHub の設定は画面からも変えられますが、画面操作は記録が残らず、再現もできません。
このリポジトリでは **可能な限りファイルとスクリプトで管理** し、API で設定できないものだけを手動手順として残します。

| 設定項目 | 実現方法 | 管理場所 | 反映タイミング |
|---|---|---|---|
| Issue テンプレート | Issue Forms（YAML） | `.github/ISSUE_TEMPLATE/*.yml` | main に入った時点で有効 |
| PR テンプレート | Markdown | `.github/pull_request_template.md` | 同上 |
| レビュアー自動割当 | CODEOWNERS | `.github/CODEOWNERS` | 同上 |
| 依存関係の自動更新 | Dependabot version updates | `.github/dependabot.yml` | 同上（週次） |
| ラベル | YAML定義 + 同期ワークフロー | `.github/labels.yml` + `.github/workflows/labels.yml` | main に push されると自動同期 |
| PRタイトル検証（Conventional Commits） | GitHub Actions | `.github/workflows/pr-title.yml` | PR作成・編集時 |
| マージ方式・説明・Topics | REST API（`gh api`） | `scripts/github/setup-repo.sh` | スクリプト実行時 |
| セキュリティ機能 | REST API | 同上 | 同上 |
| Milestones | REST API | 同上 | 同上 |
| main ブランチ保護 | **Repository Rulesets**（REST API） | 同上 | 同上 |
| GitHub Projects とフィールド | `gh project`（GraphQL） | `scripts/github/setup-project.sh` | スクリプト実行時 |
| Projects の Iteration・ビュー・自動化 | **手動**（API 未対応） | 本ドキュメント §5 | 手動 |

---

## 2. ファイルで管理する設定（`.github/`）

```
.github/
├── ISSUE_TEMPLATE/
│   ├── config.yml           # 空のIssueを禁止し、テンプレート選択を強制
│   ├── epic.yml             # Epic（type:epic）
│   ├── story.yml            # ユーザーストーリー（type:story）
│   ├── task.yml             # 技術タスク（type:task）
│   ├── bug.yml              # 不具合（type:bug）
│   ├── spike.yml            # 技術検証（type:spike）
│   └── search-quality.yml   # 検索品質の不具合（type:bug + area:search）
├── pull_request_template.md
├── CODEOWNERS
├── dependabot.yml
├── labels.yml               # ラベル定義の正本
└── workflows/
    ├── labels.yml           # labels.yml → GitHub ラベルの同期
    └── pr-title.yml         # PRタイトルの Conventional Commits 検証
```

### 2.1 Issue テンプレート（Issue Forms）

- `.github/ISSUE_TEMPLATE/` に YAML を置くと、Issue 作成画面がフォーム形式になる。
- 各テンプレートの `labels:` で `type:*` ラベルが自動で付く。
- `config.yml` の `blank_issues_enabled: false` で、テンプレートを使わない Issue を作れなくしている。

### 2.2 ラベルの同期

- 正本は `.github/labels.yml`。名前・色・説明をここで管理する。
- main に push されると `labels` ワークフロー（[crazy-max/ghaction-github-labeler](https://github.com/crazy-max/ghaction-github-labeler)）が GitHub 上のラベルを同期する。
- `skip-delete: false` のため、**ファイルに無いラベルは削除される**（GitHub のデフォルトラベル `bug` `enhancement` 等も削除）。ラベルを画面から追加しても次回同期で消えるので、必ず YAML を編集して PR を出す。
- 手動で再同期したい場合: Actions タブ → `labels` → `Run workflow`。

### 2.3 PR タイトル検証

- squash merge では **PRタイトルがそのまま main のコミットメッセージ** になる。そのため、コミットごとではなく PR タイトルを検証する。
- [amannn/action-semantic-pull-request](https://github.com/amannn/action-semantic-pull-request) で `type(scope): subject` 形式を検証する。type・scope の許可リストは GITHUB_WORKFLOW.md §3 と一致させる。
- このジョブ（`conventional-commits`）は main の必須チェックに登録している（§3.4）。

### 2.4 Dependabot

- 現時点では `github-actions` のみ（ワークフローで使う Action の更新）。
- `backend/`（gradle）、`frontend/`（npm）、`ai-service/`（uv）を作ったタイミングで `updates:` に追記する。存在しないディレクトリを指定するとエラーになるため、先に書かない。

---

## 3. スクリプトで管理する設定（`scripts/github/setup-repo.sh`）

### 3.1 実行方法

```bash
# 前提: GitHub CLI をインストールし、admin 権限のあるアカウントでログイン
gh auth login
scripts/github/setup-repo.sh                         # 既定: Mika777v/knowledge-navigator
scripts/github/setup-repo.sh someone/fork-of-this    # フォーク等に適用する場合
```

スクリプトは **冪等**（何度実行しても同じ状態になる）。設定を変えたいときはスクリプトを編集して PR を出し、マージ後に再実行する。

### 3.2 基本設定

| 設定 | 値 | 理由 |
|---|---|---|
| マージ方式 | squash のみ（merge commit / rebase は無効） | main の履歴を「1 PR = 1 コミット」に保つ |
| squash のコミットタイトル/本文 | PRタイトル / PR本文 | Conventional Commits を PR タイトルで担保するため |
| マージ後のブランチ削除 | 有効 | ブランチの散らかり防止 |
| Auto-merge | 有効 | CI 通過後に自動マージできる |
| Update branch ボタン | 有効 | main への追従を簡単に |
| Wiki | 無効 | ドキュメントは `docs/` に一本化 |
| Topics | rag, spring-boot, nextjs, ... | ポートフォリオとして検索されやすくする |

### 3.3 セキュリティ

| 機能 | API | 内容 |
|---|---|---|
| Dependabot alerts | `PUT /repos/{repo}/vulnerability-alerts` | 依存関係の脆弱性を通知 |
| Dependabot security updates | `PUT /repos/{repo}/automated-security-fixes` | 脆弱性修正の PR を自動作成 |
| Secret scanning | `security_and_analysis.secret_scanning` | コミット済みの秘密情報を検出 |
| Push protection | `security_and_analysis.secret_scanning_push_protection` | 秘密情報を含む push をブロック |

CodeQL は解析対象のコード（Java / TypeScript）ができてから `codeql.yml` で追加する（E16-T1）。

### 3.4 main ブランチ保護（Repository Rulesets）

旧来の「Branch protection rules」ではなく、**Rulesets** を使う。
JSON で丸ごと定義でき、画面でも API でも同じ構造で扱えるため、コード管理に向いている。

| ルール | 設定 | 意味 |
|---|---|---|
| 対象 | `~DEFAULT_BRANCH`（main） | デフォルトブランチ名が変わっても追従 |
| `pull_request` | 必須、承認数 0、会話の解決必須、squash のみ | 直接 push 禁止。1人開発のため自分の PR を承認できない問題を承認数 0 で回避 |
| `required_status_checks` | `conventional-commits` | PRタイトル検証が通らないとマージ不可 |
| `non_fast_forward` | 有効 | force push 禁止 |
| `deletion` | 有効 | main の削除禁止 |
| `required_linear_history` | 有効 | マージコミット禁止 |
| `bypass_actors` | なし | **管理者も PR 経由**（設計どおり運用するため） |

**必須チェックの追加方法**: backend / frontend の CI ワークフローを作ったら、スクリプトの `required_status_checks` にジョブ名（例: `backend-build`）を追加して再実行する。
**まだ存在しないジョブを必須にすると、どの PR もマージできなくなる**ため、ワークフローが main で一度動いてから追加する。

確認方法: Settings → Rules → Rulesets → `main-protection`、または
`gh api repos/Mika777v/knowledge-navigator/rulesets`

### 3.5 Milestones

ROADMAP.md のリリースと期日に合わせて作成する。

| Milestone | 期日 |
|---|---|
| v0.1.0 MVP | 2026-12-27 |
| v0.2.0 PDF | 2027-01-31 |
| v0.3.0 Knowledge | 2027-02-28 |
| v0.4.0 Hybrid | 2027-03-28 |
| v0.5.0 RAG | 2027-04-25 |
| v0.6.0 Assistant | 2027-04-30 |

---

## 4. GitHub Projects（`scripts/github/setup-project.sh`）

```bash
gh auth refresh -s project      # Projects 操作には project スコープが必要
scripts/github/setup-project.sh
```

| 処理 | 内容 |
|---|---|
| プロジェクト作成 | ユーザー Mika777v 配下に「Knowledge Navigator Roadmap」（無ければ作成） |
| フィールド追加 | Phase / Priority / Size / Area / Target Release（単一選択） |
| リンク | リポジトリの Projects タブに表示されるよう紐づけ |

Projects (v2) はリポジトリではなく **ユーザー（または Organization）に属する** ため、リポジトリの設定とはスクリプトを分けている。

---

## 5. 手動で行う設定（API 未対応）

以下は GitHub の API で作成できないため、画面で設定する。

### 5.1 Status フィールドの選択肢

既定の `Todo / In Progress / Done` を編集し、`Backlog / Ready / In Progress / In Review / Done` にする。
（プロジェクト → 右上 `…` → Settings → Status）

### 5.2 Iteration フィールド

Settings → `+ New field` → 種類 **Iteration**、名前 `Iteration`、期間 2 週間、開始日 **2026-11-02**（Sprint 1）。

### 5.3 ビュー

| ビュー名 | レイアウト | 設定 |
|---|---|---|
| Board | Board | 列: Status |
| Current Sprint | Table | フィルタ: `iteration:@current` |
| Roadmap | Roadmap | 日付: Iteration、グループ: Target Release |
| By Epic | Table | グループ: Parent issue |
| Search Quality | Table | フィルタ: `label:area:search` |
| Bugs | Table | フィルタ: `label:type:bug` |

### 5.4 ワークフロー（Projects の自動化）

プロジェクト → `…` → Workflows で以下を有効にする。

| ワークフロー | 設定 |
|---|---|
| Item added to project | Status を `Backlog` にする |
| Auto-add to project | リポジトリ `knowledge-navigator`、フィルタ `is:issue,pr is:open` |
| Pull request linked to issue | Status を `In Review` にする |
| Item closed / Pull request merged | Status を `Done` にする |

### 5.5 Actions の権限

Settings → Actions → General → Workflow permissions は既定の **Read repository contents** のままにする。
書き込みが必要なワークフローは、ファイル内の `permissions:` で個別に許可する（`labels.yml` の `issues: write` など）。

---

## 6. 新しく設定を変えるときの流れ

```
1. ブランチ作成（例: ci/42-add-backend-check）
2. .github/ や scripts/github/ を編集
3. PR → pr-title チェック → squash merge
4. スクリプトで管理している設定なら、main で scripts/github/setup-*.sh を再実行
5. 手動設定を変えた場合は、本ドキュメント §5 を更新
```

---

## 7. ブートストラップについて

初期設定のコミット（このドキュメントを含む）は、ブランチ保護を有効にする **前に** main へ直接 push した。
保護を有効にした後は、例外なく PR 経由で変更する。
