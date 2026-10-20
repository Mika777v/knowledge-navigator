#!/usr/bin/env bash
# リポジトリ設定・セキュリティ・Milestone・main ブランチ保護（Ruleset）を適用する。
# 何度実行しても同じ状態になる（冪等）。
# 前提: gh CLI がリポジトリの admin 権限で認証済み（gh auth login）
# 使い方: scripts/github/setup-repo.sh [owner/repo]
set -euo pipefail

REPO="${1:-Mika777v/knowledge-navigator}"
echo "==> target: ${REPO}"

# ---------------------------------------------------------------
# 1. 基本設定（マージ方式・説明・Wiki など）
# ---------------------------------------------------------------
echo "==> repository settings"
gh api -X PATCH "repos/${REPO}" --silent \
  -f description="社内規程ナビ — 知りたいことから制度・根拠条文・問い合わせ先へ案内するナレッジナビゲーション（架空データ）" \
  -F has_wiki=false \
  -F has_issues=true \
  -F has_projects=true \
  -F allow_squash_merge=true \
  -F allow_merge_commit=false \
  -F allow_rebase_merge=false \
  -F allow_auto_merge=true \
  -F allow_update_branch=true \
  -F delete_branch_on_merge=true \
  -f squash_merge_commit_title=PR_TITLE \
  -f squash_merge_commit_message=PR_BODY

echo "==> topics"
gh api -X PUT "repos/${REPO}/topics" --silent --input - <<'JSON'
{"names":["rag","spring-boot","nextjs","fastapi","pgvector","postgresql","japanese-search","knowledge-management","portfolio"]}
JSON

# ---------------------------------------------------------------
# 2. セキュリティ（Dependabot / Secret scanning / Push protection）
# ---------------------------------------------------------------
echo "==> security features"
gh api -X PUT "repos/${REPO}/vulnerability-alerts" --silent
gh api -X PUT "repos/${REPO}/automated-security-fixes" --silent
gh api -X PATCH "repos/${REPO}" --silent --input - <<'JSON'
{"security_and_analysis":{
  "secret_scanning":{"status":"enabled"},
  "secret_scanning_push_protection":{"status":"enabled"}
}}
JSON

# ---------------------------------------------------------------
# 3. Milestones（Release と対応。docs/ROADMAP.md の期日）
# ---------------------------------------------------------------
echo "==> milestones"
create_milestone() {
  local title="$1" due="$2" desc="$3"
  if gh api "repos/${REPO}/milestones?state=all&per_page=100" --jq '.[].title' | grep -Fxq "$title"; then
    echo "    exists: ${title}"
  else
    gh api -X POST "repos/${REPO}/milestones" --silent \
      -f title="$title" -f due_on="${due}T23:59:59Z" -f description="$desc"
    echo "    created: ${title}"
  fi
}
create_milestone "v0.1.0 MVP"        2026-12-27 "Phase1: 構造化ナビゲーション（AIなし）"
create_milestone "v0.2.0 PDF"        2027-01-31 "Phase2: PDF取込・全文検索・タグ"
create_milestone "v0.3.0 Knowledge"  2027-02-28 "Phase3: 類義語・正規化・推薦"
create_milestone "v0.4.0 Hybrid"     2027-03-28 "Phase4: Embedding・ハイブリッド検索"
create_milestone "v0.5.0 RAG"        2027-04-25 "Phase5: 根拠付き回答生成"
create_milestone "v0.6.0 Assistant"  2027-04-30 "Phase6: AIアシスタント試作"

# ---------------------------------------------------------------
# 4. main ブランチ保護（Repository Ruleset）
#    - PR 必須（1人開発のため承認数は 0、会話の解決は必須）
#    - 必須チェック: pr-title ワークフローの conventional-commits ジョブ
#      （backend / frontend の CI を作ったら required_status_checks に追加する）
#    - force push・削除禁止、linear history
#    - bypass なし（管理者も PR 経由）
# ---------------------------------------------------------------
echo "==> ruleset: main-protection"
RULESET_JSON=$(cat <<'JSON'
{
  "name": "main-protection",
  "target": "branch",
  "enforcement": "active",
  "conditions": { "ref_name": { "include": ["~DEFAULT_BRANCH"], "exclude": [] } },
  "bypass_actors": [],
  "rules": [
    { "type": "deletion" },
    { "type": "non_fast_forward" },
    { "type": "required_linear_history" },
    { "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 0,
        "dismiss_stale_reviews_on_push": true,
        "require_code_owner_review": false,
        "require_last_push_approval": false,
        "required_review_thread_resolution": true,
        "allowed_merge_methods": ["squash"]
      } },
    { "type": "required_status_checks",
      "parameters": {
        "strict_required_status_checks_policy": false,
        "required_status_checks": [
          { "context": "conventional-commits" }
        ]
      } }
  ]
}
JSON
)
RULESET_ID=$(gh api "repos/${REPO}/rulesets" --jq '.[] | select(.name=="main-protection") | .id')
if [[ -n "${RULESET_ID}" ]]; then
  echo "${RULESET_JSON}" | gh api -X PUT "repos/${REPO}/rulesets/${RULESET_ID}" --silent --input -
  echo "    updated: ${RULESET_ID}"
else
  echo "${RULESET_JSON}" | gh api -X POST "repos/${REPO}/rulesets" --silent --input -
  echo "    created"
fi

echo "==> done"
