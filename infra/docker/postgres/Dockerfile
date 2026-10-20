# PostgreSQL 17 + pgvector + pg_bigm
#
# - ベースは pgvector 公式イメージ（PostgreSQL 公式イメージ + pgvector）
# - pg_bigm はソースからビルドし、成果物だけを最終イメージにコピーする（ビルドツールを残さない）
# - FROM のタグは Dependabot（docker）が更新する。2か所とも同じタグにすること
#
# ビルド: docker build -t knowledge-navigator/postgres infra/docker/postgres

# ---------- pg_bigm のビルド ----------
FROM pgvector/pgvector:0.8.7-pg17-bookworm AS pg-bigm-builder

ARG PG_BIGM_VERSION=1.2-20240606
# ソース tarball の SHA-256。PG_BIGM_VERSION を変えたら必ず更新する
ARG PG_BIGM_SHA256=69eabf7a6af19909fc8a33525768854b08c94929d7f9fa94fe31e737c04748ff

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      build-essential \
      ca-certificates \
      curl \
      "postgresql-server-dev-${PG_MAJOR}" \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /build
# with_llvm=no: PostgreSQL の Debian パッケージは LLVM(JIT) 有効でビルドされており、
# そのままだと clang で bitcode を生成しようとして失敗するため無効化する
RUN curl -fsSL -o pg_bigm.tar.gz \
      "https://github.com/pgbigm/pg_bigm/archive/refs/tags/v${PG_BIGM_VERSION}.tar.gz" \
 && sha256sum pg_bigm.tar.gz \
 && echo "${PG_BIGM_SHA256}  pg_bigm.tar.gz" | sha256sum -c - \
 && tar xzf pg_bigm.tar.gz --strip-components=1 \
 && make USE_PGXS=1 with_llvm=no \
 && make USE_PGXS=1 with_llvm=no install DESTDIR=/out

# ---------- 実行イメージ ----------
FROM pgvector/pgvector:0.8.7-pg17-bookworm

LABEL org.opencontainers.image.title="knowledge-navigator-postgres" \
      org.opencontainers.image.description="PostgreSQL 17 with pgvector and pg_bigm for Knowledge Navigator" \
      org.opencontainers.image.source="https://github.com/Mika777v/knowledge-navigator"

COPY --from=pg-bigm-builder /out/ /
# 初回起動時（データディレクトリが空のとき）だけ実行される
COPY initdb/ /docker-entrypoint-initdb.d/
