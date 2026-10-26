# ADR-0008: LLM・Embedding のプロバイダは ai-service の薄い自前の抽象で切り替え、既定はローカルの Ollama（bge-m3）にする

- ステータス: Accepted
- 日付: 2026-10-26
- 関連 Issue / PR: E13-T2（TASKS.md）、E14-T7、#17（Epic E13）、#137
- 関連ドキュメント: [ARCHITECTURE.md §5.2・§5.6・§6](../ARCHITECTURE.md)、[TECH_STACK_DECISION.md D-06・D-07](../TECH_STACK_DECISION.md)、[ADR-0006](./0006-ai-service-stateless.md)、[ADR-0007](./0007-pgvector.md)

## 背景（Context）

- Embedding（v0.4.0）と回答文の生成（v0.5.0）には、モデルを動かす「プロバイダ」が要る。候補は、手元で動かす Ollama と、クラウドの OpenAI・Azure OpenAI。
- このリポジトリはポートフォリオで、見る人が **API キーなしで** `docker compose --profile ai up` だけで試せることが大事。また「社内の文書を外部に送らない構成」を実際に示したい。
- 実務では、閉域で使える Azure OpenAI などに替えたくなる。プロバイダを替えるたびに、呼び出す側のコードを直したくない。
- 外部のプロバイダに送ると、文章が社外に出る。公開範囲ごとに、送ってよいかを決める必要がある（ARCHITECTURE.md §6「LLM 送信」）。
- LangChain などのフレームワークは変化が速く、抽象が厚い（TECH_STACK_DECISION D-07）。
- CI では、モデルを取り寄せて動かすのは重く、結果も揺れる。

## 決定（Decision）

1. **ai-service の中に、薄い自前のインターフェースを置く。** `EmbeddingModel`（文章の並び → ベクトルの並び）と、v0.5.0 から使う `ChatModel`（メッセージ → 回答文のストリーム）。LangChain・LlamaIndex は使わない。
2. **実装は4つ。** 環境変数で選ぶ（Embedding と生成は別々に選べる）。
   | 名前 | 中身 | 使う場面 |
   |---|---|---|
   | `ollama`（既定） | Ollama の HTTP API を直接呼ぶ | ローカル・デモ |
   | `openai` | OpenAI の公式 SDK | 比較・実務想定 |
   | `azure_openai` | 同じ SDK の Azure 向けの設定 | 実務想定（閉域・データの所在地） |
   | `fake` | 文章のハッシュから決まったベクトルを返す。外部に通信しない | テスト・CI |
   - 環境変数: `EMBEDDING_PROVIDER`・`EMBEDDING_MODEL`、`CHAT_PROVIDER`・`CHAT_MODEL`、API キーなど。
3. **既定の Embedding のモデルは `bge-m3`**（多言語・日本語に対応、1024 次元、MIT ライセンス）。OpenAI・Azure では `text-embedding-3-small` を、`dimensions=1024` を指定して使い、ADR-0007 の次元にそろえる。生成のモデルは v0.5.0 で決める。
4. **Ollama は compose の `ai` プロファイルで動かす。** モデルは初回の起動で取り寄せ、ボリュームに残す。GPU なし（CPU）でも動く設定を既定にする。
5. **外部に送ってよい公開範囲は backend が決める。** ai-service は `GET /v1/info` で、今のプロバイダ・モデル・次元・外部に送るかどうか（`ollama` と `fake` は送らない）を返す。backend は、外部に送るプロバイダのときは設定 `knavi.ai.external-visibilities`（既定は `INTERNAL` だけ）に入る公開範囲の文章だけを送る。外部に送らないプロバイダには `CONFIDENTIAL` 以外を送る。`CONFIDENTIAL` はどのプロバイダにも送らない。
   - 利用者の**質問文**は、外部のプロバイダのときもベクトルにするために送る。そのことを README・画面の注記に書く。
6. **モデル名を保存する。** backend は `GET /v1/info` のモデル名で、ベクトルの保存（`embeddings.model`）と検索の絞り込みを行う。モデルを替えると、新しいモデルでベクトルを作り直すまでベクトル検索は使われず、キーワード検索だけで動く（ADR-0007）。

## 検討した選択肢（Options）

### 抽象の作り方

| 選択肢 | 長所 | 短所 |
|---|---|---|
| 薄い自前のインターフェース（採用） | 中身が数十行で読める。使う機能（Embedding・ストリーミング生成）だけを持つ。ライブラリの変化に引きずられない | 新しいプロバイダは自分で実装する |
| LangChain / LlamaIndex | 多くのプロバイダ・部品がそろっている | 抽象が厚く、版の変化が速い。仕組みが見えにくい |
| LiteLLM | 多くのプロバイダを OpenAI と同じ形で呼べる | 依存が大きい。今は3つで足りる |

### 既定のプロバイダ

| 選択肢 | 長所 | 短所 |
|---|---|---|
| Ollama（採用） | API キー・費用が要らない。文章が外部に出ない。モデルを取り替えやすい | CPU だと遅い。モデルの取り寄せに時間とディスク（bge-m3 で約 1.2GB）がかかる |
| OpenAI | 品質が高く、速い。手元の性能に左右されない | API キーと費用が要る。文章が外部に出る |
| llama.cpp / vLLM のサーバー | 速度を細かく調整できる | 設定が多く、デモの手軽さで Ollama に劣る |

### 既定の Embedding のモデル

| 選択肢 | 長所 | 短所 |
|---|---|---|
| bge-m3（採用） | 日本語を含む多言語で評価が高い。Ollama で配布されている。MIT ライセンス | 1024 次元でやや大きい。CPU では少し遅い |
| multilingual-e5 系 | 軽いモデルがある | Ollama の公式の配布がない |
| 日本語専用のモデル | 日本語の精度が高いことがある | Ollama で動かす手間がかかる。ライセンスの確認が要る |

## 結果（Consequences）

### 良い影響

- API キーなしで、外部に文章を送らずに意味検索を試せる。
- 設定（環境変数）だけで Azure OpenAI などに替えられ、backend は替えなくてよい。
- CI は `fake` を使うので、モデルなしで速く、結果も毎回同じになる。
- 外部に送るかどうかの判断が backend の1か所にあり、公開範囲の規則と並べて確かめられる。

### 悪い影響・受け入れるリスク

- `fake` のベクトルは意味を持たないので、CI では検索の**品質**は測れない。品質は手元で Ollama を動かして `make eval` で測る。
- ローカルの Ollama は CPU だと遅く、Embedding の作り直しに時間がかかる（ダミーデータでは数分程度を見込む）。
- 外部のプロバイダのとき、既定では `INTERNAL` 以外の文章はベクトルにならず、それらはキーワード検索だけで探すことになる。

## 検証方法（Compliance）

- 各実装が同じインターフェースのテスト（入出力の形・次元・エラーの扱い）を通ることを pytest で確かめる（`openai`・`azure_openai` は通信を差し替えて確かめる）。
- 外部に送るプロバイダのとき、`INTERNAL` 以外・`CONFIDENTIAL` の文章を ai-service に送らないことを backend のテストで確かめる。
- ai-service の依存に LangChain・LlamaIndex が入っていないことをレビューで確かめる。

## 見直す条件（Revisit when）

- 扱うプロバイダが増え、自前の実装の手間が大きくなったとき（LiteLLM などを比べる）。
- bge-m3 では評価の目標に届かないとき、または日本語により強いモデルが Ollama で使えるようになったとき。
- 生成のモデル（v0.5.0）で、ストリーミング以外の機能（ツールの呼び出しなど）が必要になったとき。
