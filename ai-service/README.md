# ai-service

社内規程ナビの AI 処理（Embedding、v0.5.0 からは回答文の生成）を受け持つ FastAPI のサービス。

- **DB に接続しない。** backend が権限で絞り込んだ後の文章を受け取り、計算した結果を返すだけ（[ADR-0006](../docs/adr/0006-ai-service-stateless.md)）
- `/v1/...` は backend との共有のトークン（環境変数 `AI_SERVICE_TOKEN`）を `Authorization: Bearer` で送らないと 401 になる。`/healthz` だけはトークンなしで呼べる
- プロバイダ（Ollama・OpenAI・Azure OpenAI・テスト用の fake）の切り替えは E13-T2 で足す（[ADR-0008](../docs/adr/0008-llm-provider-abstraction.md)）

## 手元で動かす

[uv](https://docs.astral.sh/uv/) を使う。

```bash
cd ai-service
uv sync                      # 依存を入れる（.venv ができる）
uv run ruff check . && uv run ruff format --check .   # 書き方の検査
uv run pytest                # テスト
AI_SERVICE_TOKEN=local uv run uvicorn app.main:app --reload --port 8000
```

compose では `ai` プロファイルで起動する（`make up-ai`）。ポートは compose の内部ネットワークにだけ出し、ブラウザからは呼べない。

## 依存の追加・更新

- 公開から2週間以上たった版を選ぶ（CLAUDE.md）。`pyproject.toml` の `[tool.uv] exclude-newer` より新しい版は uv が選ばないので、上げるときはこの日付も進めてから `uv lock` する
- LangChain・LlamaIndex は使わない（ADR-0008）
- DB のドライバー（psycopg など）は入れない（ADR-0006）
