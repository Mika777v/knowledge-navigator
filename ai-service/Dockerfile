# ai-service（FastAPI）の実行イメージ。DB には接続しない（ADR-0006）
# ビルド: docker build -t knowledge-navigator/ai-service ai-service

FROM python:3.12.14-slim-trixie AS build
# uv は pyproject.toml の依存と同じく、公開から2週間以上たった版に固定する
RUN pip install --no-cache-dir uv==0.13.0
WORKDIR /app
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy
# 依存のレイヤーをソースの変更から分ける
COPY pyproject.toml uv.lock .python-version ./
RUN uv sync --frozen --no-dev --no-install-project
COPY app app

FROM python:3.12.14-slim-trixie
RUN useradd --system --uid 10001 --no-create-home knavi
WORKDIR /app
COPY --from=build /app/.venv /app/.venv
COPY --from=build /app/app /app/app
ENV PATH="/app/.venv/bin:$PATH" PYTHONUNBUFFERED=1
USER knavi
EXPOSE 8000
# curl を入れずに、Python でヘルスチェックする
HEALTHCHECK --interval=10s --timeout=3s --retries=30 \
  CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://localhost:8000/healthz', timeout=2)"]
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
