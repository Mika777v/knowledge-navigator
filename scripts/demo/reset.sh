#!/usr/bin/env bash
# デモ環境のデータを入れ直す（ADR-0015 決定 4。cron で毎日動かす）。
# DB を空にし、S3 の原本を消し、backend を起動し直して（Flyway でテーブルを作る）、ダミーデータを入れる。
# CI では KNAVI_COMPOSE_FILES で compose.demo.ci.yaml を足して、.env.demo を用意してから呼ぶ
set -euo pipefail
cd "$(dirname "$0")/../.."

read -r -a files <<< "${KNAVI_COMPOSE_FILES:--f compose.demo.yaml}"
compose=(docker compose "${files[@]}" --env-file .env.demo)
# shellcheck source=/dev/null
source .env.demo

"${compose[@]}" stop frontend backend
"${compose[@]}" run --rm db-reset
# S3 そのもの（接続先が空）のときだけ、原本を消す。CI の SeaweedFS は使い捨て
if [ -z "${KNAVI_STORAGE_ENDPOINT:-}" ]; then
  aws s3 rm "s3://$KNAVI_STORAGE_BUCKET" --recursive --region "$AWS_REGION" --only-show-errors
fi
"${compose[@]}" up -d --wait backend frontend
"${compose[@]}" run --rm --no-deps -v "$PWD/data/seed:/seed:ro" -v "$PWD/data/pdf:/pdf:ro" backend \
  --spring.main.web-application-type=none --knavi.seed.enabled=true --knavi.seed.path=/seed \
  --knavi.seed.pdf-path=/pdf
