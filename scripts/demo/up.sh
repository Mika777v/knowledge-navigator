#!/usr/bin/env bash
# デモ環境を起動する（版を上げるときも同じ）。EC2 の上で、リポジトリの直下から: scripts/demo/up.sh v0.7.0
# 初めての起動のあとは、scripts/demo/reset.sh でダミーデータを入れる
set -euo pipefail
cd "$(dirname "$0")/../.."

scripts/demo/env.sh "${1:?版（例: v0.7.0）を指定してください}"
compose=(docker compose -f compose.demo.yaml --env-file .env.demo)
"${compose[@]}" pull
# まっさらな RDS では拡張が無い。Flyway は vector を作らないため、起動の前に作っておく（何度流してもよい）
"${compose[@]}" --profile tools pull psql
"${compose[@]}" run --rm -T psql -f /dev/stdin < infra/demo/extensions.sql
"${compose[@]}" up -d --wait --remove-orphans
