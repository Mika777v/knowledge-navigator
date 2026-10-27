#!/usr/bin/env bash
# 意味検索の評価の前に、ベクトルの差分合わせ（ADR-0012）が終わるまで待つ。
# embeddings の件数が 0 より多く、合わせる間隔より長く変わらなくなったら終わったとみなす。
# 使い方: scripts/eval/wait_embeddings.sh [変わらない時間（秒。既定 90）] [待つ上限（秒。既定 1800）]
set -euo pipefail

stable_seconds="${1:-90}"
timeout_seconds="${2:-1800}"
poll=15

count() {
  docker compose exec -T postgres sh -c \
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "SELECT count(*) FROM embeddings"' | tr -d '[:space:]'
}

last=""
unchanged=0
elapsed=0
while (( elapsed < timeout_seconds )); do
  current="$(count)"
  if [[ "$current" == "$last" && "$current" != "0" ]]; then
    unchanged=$(( unchanged + poll ))
  else
    unchanged=0
  fi
  echo "embeddings: ${current} 件（${unchanged} 秒変わらず）"
  if (( unchanged >= stable_seconds )); then
    echo "ベクトルの合わせが終わりました"
    exit 0
  fi
  last="$current"
  sleep "$poll"
  elapsed=$(( elapsed + poll ))
done
echo "時間内にベクトルの合わせが終わりませんでした。docker compose logs backend ai-service を確かめてください" >&2
exit 1
