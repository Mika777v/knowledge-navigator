# ai-service

社内規程ナビの AI 処理（Embedding、v0.5.0 からは回答文の生成）を受け持つ FastAPI のサービス。

- **DB に接続しない。** backend が権限で絞り込んだ後の文章を受け取り、計算した結果を返すだけ（[ADR-0006](../docs/adr/0006-ai-service-stateless.md)）
- `/v1/...` は backend との共有のトークン（環境変数 `AI_SERVICE_TOKEN`）を `Authorization: Bearer` で送らないと 401 になる。`/healthz` だけはトークンなしで呼べる
- `POST /v1/embeddings`（`{"texts": [...]}` → 文章ごとの 1024 次元のベクトル）。1回に 64 件・1件 8000 文字まで
- `POST /v1/rag/answer`（質問と、backend が番号を付けた条 → 根拠付きの回答文。下の「回答文の生成」）
- `GET /v1/info` で、今のプロバイダ・モデル・次元・社外に送るか（`external`）を、Embedding と生成（`chat`）それぞれについて返す。backend はこれを見て送る文章を絞る（ADR-0008）

## プロバイダ（[ADR-0008](../docs/adr/0008-llm-provider-abstraction.md)）

環境変数 `EMBEDDING_PROVIDER` で選ぶ。

| 値 | 中身 | 社外に送るか |
|---|---|---|
| `ollama`（既定） | compose の Ollama（`bge-m3`、1024 次元） | 送らない |
| `openai` | OpenAI（`OPENAI_API_KEY`。`EMBEDDING_MODEL=text-embedding-3-small`、`dimensions=1024` を指定） | 送る |
| `azure_openai` | Azure OpenAI（`AZURE_OPENAI_API_KEY`・`AZURE_OPENAI_ENDPOINT`。`EMBEDDING_MODEL` はデプロイ名） | 送る |
| `fake` | 文章のハッシュから決まったベクトル。テスト・CI 用 | 送らない |

回答文の生成は、環境変数 `CHAT_PROVIDER`・`CHAT_MODEL` で別に選ぶ（[ADR-0013](../docs/adr/0013-rag-generation-model-and-delivery.md)）。

| 値 | 中身 | 社外に送るか |
|---|---|---|
| `ollama`（既定） | compose の Ollama（既定のモデルは `qwen3.5:4b`。考える過程（thinking）を切り、温度 0） | 送らない |
| `openai` / `azure_openai` | `CHAT_MODEL` が必須（Azure はデプロイ名）。既定のモデル名は持たない | 送る |
| `fake` | 文脈の1番目の条の最初の文に `[1]` を付けて返す。テスト・CI 用 | 送らない |

必要なキー・モデル名が足りないときは、起動の時点で止まる。

## 回答文の生成（`POST /v1/rag/answer`）

```json
{"question": "結婚祝金はいくら？",
 "passages": [{"ref": 1, "title": "慶弔見舞金規程 第5条（結婚祝金）", "text": "…30,000円を支給する。…"}]}
```

- `passages` は検索の順位の順。backend が権限で絞り、番号（`ref`）を付けたもの。窓口・根拠の表示は backend が DB の値で描く（[ADR-0009](../docs/adr/0009-contacts-and-evidence-from-db.md)）
- 文脈（E14-T1）: 渡された順に `<passage ref="n" title="…">` で囲んで並べる。全体 6000 文字・1件 1500 文字を上限にし、入らない下位の条は落とす（1件目は切り詰めて必ず入れる）。本文の中の `<passage` は全角にして、区切りの外に抜け出せないようにする
- 応答: `answerable`・`reason`・`answer`・`citations`（回答文の `[n]`）・`model`
  | `reason` | 意味 |
  |---|---|
  | `ANSWERED` | 回答できた |
  | `NO_EVIDENCE` | 条が1件もなく、LLM を呼ばなかった |
  | `NOT_IN_CONTEXT` | LLM が「該当する記載が見つかりません。」と答えた |
- プロバイダの失敗・時間切れ（110 秒）は 502。backend は 4ブロックの結果を残して「回答できない」にする
- 引用の番号・数値・連絡先の照合（E14-T3）はまだ入っていない

## 手元で動かす

[uv](https://docs.astral.sh/uv/) を使う。

```bash
cd ai-service
uv sync                      # 依存を入れる（.venv ができる）
uv run ruff check . && uv run ruff format --check .   # 書き方の検査
uv run pytest                # テスト
AI_SERVICE_TOKEN=local uv run uvicorn app.main:app --reload --port 8000
```

compose では `ai` プロファイルで起動する（`make up-ai`）。Ollama と、モデルの取り寄せ（初回だけ。bge-m3 で約 1.2GB、qwen3.5:4b で数 GB）も一緒に動く。ポートは compose の内部ネットワークにだけ出し、ブラウザからは呼べない。

## 依存の追加・更新

- 公開から2週間以上たった版を選ぶ（CLAUDE.md）。`pyproject.toml` の `[tool.uv] exclude-newer` より新しい版は uv が選ばないので、上げるときはこの日付も進めてから `uv lock` する
- LangChain・LlamaIndex は使わない（ADR-0008）
- DB のドライバー（psycopg など）は入れない（ADR-0006）
