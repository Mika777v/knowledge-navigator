#!/usr/bin/env bash
# GitHub Projects (v2)「Knowledge Navigator Roadmap」を作成し、カスタムフィールドを追加して
# リポジトリにリンクする。既にあればフィールドの不足分だけ追加する（冪等）。
# 前提: gh auth login 済み、かつ project スコープあり（gh auth refresh -s project）
# 注意: Iteration フィールドとビューは API で作れないため手動（docs/REPOSITORY_SETUP.md 参照）
# 使い方: scripts/github/setup-project.sh [owner] [repo]
set -euo pipefail

OWNER="${1:-Mika777v}"
REPO="${2:-knowledge-navigator}"
TITLE="Knowledge Navigator Roadmap"

NUMBER=$(gh project list --owner "$OWNER" --format json --jq ".projects[] | select(.title==\"${TITLE}\") | .number")
if [[ -z "${NUMBER}" ]]; then
  NUMBER=$(gh project create --owner "$OWNER" --title "$TITLE" --format json --jq '.number')
  echo "==> created project #${NUMBER}"
else
  echo "==> project exists #${NUMBER}"
fi

EXISTING=$(gh project field-list "$NUMBER" --owner "$OWNER" --format json --jq '.fields[].name')

add_select_field() {
  local name="$1" options="$2"
  if grep -Fxq "$name" <<<"$EXISTING"; then
    echo "    field exists: ${name}"
  else
    gh project field-create "$NUMBER" --owner "$OWNER" --name "$name" \
      --data-type SINGLE_SELECT --single-select-options "$options" >/dev/null
    echo "    field created: ${name}"
  fi
}

# Status は Projects 既定のフィールド（Todo / In Progress / Done）を UI で編集する
add_select_field "Phase"          "P0 Setup,P1 MVP,P2 Search+,P3 Knowledge,P4 Vector,P5 RAG,P6 Assistant"
add_select_field "Priority"       "P0,P1,P2,P3"
add_select_field "Size"           "XS,S,M,L,XL"
add_select_field "Area"           "backend,frontend,ai,ingest,db,search,auth,infra,docs"
add_select_field "Target Release" "v0.1.0,v0.2.0,v0.3.0,v0.4.0,v0.5.0,v0.6.0"

gh project link "$NUMBER" --owner "$OWNER" --repo "${OWNER}/${REPO}" >/dev/null 2>&1 || true
echo "==> linked to ${OWNER}/${REPO}"
gh project view "$NUMBER" --owner "$OWNER" --format json --jq '.url'
