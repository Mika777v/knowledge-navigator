# ADR（Architecture Decision Records）

設計上の重要な判断と、その理由を記録する。
「なぜこうなっているのか」を後から辿れるようにするのが目的で、決定を変えるときは既存の ADR を書き換えず、新しい ADR を作って古いものを `Superseded` にする。

## 一覧

| No | タイトル | ステータス |
|---|---|---|
| [0001](./0001-modular-monolith-spring-boot.md) | バックエンドは Spring Boot のモジュラーモノリスで構築する | Accepted |
| [0002](./0002-japanese-search-pg-bigm.md) | 日本語全文検索に PostgreSQL + pg_bigm を採用する | Accepted |
| [0003](./0003-program-as-first-class-entity.md) | 制度（Program）を文書から独立したエンティティにする | Accepted |
| [0004](./0004-permission-prefilter.md) | 権限フィルタは検索前（プレフィルタ）に適用する | Accepted |
| [0005](./0005-nextjs-as-bff.md) | Next.js を画面兼 BFF として使う | Accepted |

### 予定（[TECH_STACK_DECISION.md §4](../TECH_STACK_DECISION.md)）

| No | タイトル | 作成時期 |
|---|---|---|
| 0006 | AI 処理を FastAPI 別サービスに分離する | Phase4 |
| 0007 | ベクトルストアに pgvector を採用する | Phase4 |
| 0008 | LLM プロバイダを抽象化し既定を Ollama とする | Phase4 |
| 0009 | AI 回答の窓口・根拠表示は LLM 生成ではなく DB 値から描画する | Phase5 |

## 書き方

1. [0000-template.md](./0000-template.md) をコピーし、`NNNN-kebab-case-title.md` で保存する（番号は連番）。
2. ステータスは `Proposed` で PR を出し、マージ時に `Accepted` にする。
3. この README の一覧に追加する。
4. 関連する Issue / PR の番号を書く。

## ADR を書くべき判断

- 後から変えるとコストが大きいもの（言語、DB、検索方式、認可方式、データモデルの中核）
- 複数の選択肢を比較して選んだもの
- 「なぜこうしなかったのか」と聞かれそうなもの
