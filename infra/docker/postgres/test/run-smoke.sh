#!/usr/bin/env bash
# PostgreSQL イメージをビルドして起動し、smoke.sql を実行する。
# 使い方:
#   infra/docker/postgres/test/run-smoke.sh            # ビルドしてテスト
#   IMAGE=some/image:tag infra/docker/postgres/test/run-smoke.sh --no-build
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
CONTEXT="$(dirname "$HERE")"
IMAGE="${IMAGE:-knowledge-navigator/postgres:smoke}"
NAME="kn-postgres-smoke-$$"

if [[ "${1:-}" != "--no-build" ]]; then
  echo "==> build ${IMAGE}"
  docker build -t "$IMAGE" "$CONTEXT"
fi

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "==> start ${NAME}"
docker run -d --name "$NAME" \
  -e POSTGRES_USER=smoke \
  -e POSTGRES_PASSWORD=smoke \
  -e POSTGRES_DB=smoke \
  "$IMAGE" >/dev/null

# 公式イメージは初期化中に一度サーバーを再起動するため、
# 初期化スクリプトのログが出て、かつ接続できるまで待つ
echo "==> wait for ready"
for _ in $(seq 1 60); do
  if docker logs "$NAME" 2>&1 | grep -q "PostgreSQL init process complete" \
     && docker exec "$NAME" pg_isready -U smoke -d smoke >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "$NAME" pg_isready -U smoke -d smoke >/dev/null || {
  docker logs "$NAME"
  echo "postgres did not become ready" >&2
  exit 1
}

echo "==> run smoke.sql"
docker exec -i "$NAME" psql -v ON_ERROR_STOP=1 -U smoke -d smoke < "$HERE/smoke.sql"
