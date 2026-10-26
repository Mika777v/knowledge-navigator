# ADR-0006: AI の処理は FastAPI の別サービス（ai-service）に分け、DB に触れない「計算だけの窓口」にする

- ステータス: Proposed
- 日付: 2026-10-26
- 関連 Issue / PR: E13-T1・E13-T3（TASKS.md）、#17（Epic E13）
- 関連ドキュメント: [ARCHITECTURE.md §2.2・§5](../ARCHITECTURE.md)、[TECH_STACK_DECISION.md D-02](../TECH_STACK_DECISION.md)、[ADR-0001](./0001-modular-monolith-spring-boot.md)、[ADR-0004](./0004-permission-prefilter.md)、[ADR-0007](./0007-pgvector.md)、[ADR-0008](./0008-llm-provider-abstraction.md)、[ADR-0010](./0010-pdf-ingestion.md)

## 背景（Context）

- v0.4.0 では、言い換えた質問（「子どもが生まれたら休める？」→ 育児休業）にも当たるように、**Embedding（文章を意味の近さで比べられる数値の並び＝ベクトルにしたもの）による意味検索**を足す。目標は、言い換えの質問セットでキーワード検索だけの場合より Hit@5 が 10 ポイント上がること（ROADMAP）。
- Embedding の計算、v0.5.0 の回答文の生成（RAG）とその評価は、Python のライブラリが最も充実している。
- 一方で、このシステムでいちばん大事なのは**権限**（公開範囲・所管部署）で、ADR-0004 で「権限は検索の前に SQL で絞る。件数からも見えないものを漏らさない」と決めている。権限の判定は backend の `AccessPolicy` に集まっている。
- ARCHITECTURE.md §2.2・§5.4 の当初の構想では、ai-service が DB を直接読んでハイブリッド検索をし、別に Python の ingest-worker が Embedding を書き込むことになっていた。この形だと、権限で絞る SQL を Python 側にも書くことになり、**認可のロジックが2か所に分かれる**。
- AI は「上乗せ」で、止まってもナビゲーション（キーワード検索）は動き続けなければならない（ARCHITECTURE.md §5.1）。

## 決定（Decision）

1. **AI の処理は Python 3.12 + FastAPI の別サービス `ai-service/` に置く。** パッケージ管理は uv、検査は ruff・pytest。compose では `ai` プロファイル（`docker compose --profile ai up`）で起動し、プロファイルなしでは今までどおり AI なしで動く。
2. **ai-service は DB に接続しない。** 受け取った文章からベクトル（v0.4.0）や回答文（v0.5.0〜）を計算して返すだけの、状態を持たないサービスにする。DB の接続情報も渡さない。
   - 権限の判定、検索の SQL（キーワード・ベクトルとも）、ベクトルの保存は、**すべて backend が行う**。ai-service に渡すのは、backend が権限で絞り込んだ後の文章だけ。
3. **Embedding のジョブは backend で動かす。** ADR-0010 と同じ PostgreSQL のキュー（`SELECT ... FOR UPDATE SKIP LOCKED`）を使う。版の公開・制度や FAQ の更新でジョブを積み、backend のワーカーが文章を区切り（チャンク分割）、`content_hash` で変わったものだけ ai-service に送り、返ってきたベクトルを保存する。Python の ingest-worker は作らない。
4. **検索のときは、backend が質問文を ai-service に送ってベクトルにし、その後の検索は backend の SQL で行う。** 呼び出しには短いタイムアウト（目安 2 秒）を付け、ai-service が止まっている・遅い・エラーのときは**キーワード検索だけで結果を返す**（利用者にはエラーを見せない）。
5. **backend と ai-service は HTTP + JSON でやりとりする。** ai-service のポートは compose の内部ネットワークにだけ出し、ブラウザ・frontend からは呼べないようにする。backend は共有のトークン（環境変数 `AI_SERVICE_TOKEN`）を `Authorization` ヘッダーで送り、ai-service はトークンが合わない要求を断る。API は `/v1/...` で版を付ける。
6. **PDF の解析は backend の PDFBox のまま（ADR-0010）にし、Python へは移さない。** 今の抽出で困っていないため。
7. ai-service の検査は、GitHub Actions の別のワークフロー（`ai-service/` が変わったときだけ動く）で行い、`ci-ok` にまとめる。

## 検討した選択肢（Options）

### AI の処理を置く場所

| 選択肢 | 長所 | 短所 |
|---|---|---|
| A. Python の別サービス。DB に触れない（採用） | 認可は backend の1か所だけ（ADR-0004 をそのまま守れる）。ai-service は文章を受けて計算するだけなので、作り・テストが単純。Python の AI ライブラリが使える | backend ↔ ai-service の通信が1往復増える。サービスが1つ増える |
| B. Python の別サービス。DB を読んで自分でハイブリッド検索する（当初の構想） | 検索と生成を ai-service の中で完結できる | 権限で絞る SQL を Python にも書くことになり、二重管理になる。片方だけ直すと情報が漏れる |
| C. backend の中で Spring AI を使う（Python なし） | サービスが増えない。通信の往復もない | RAG の評価など Python のライブラリが使えない。AI まわりの変更が backend の更新・再起動に直結する |

### Embedding を作って保存する場所

| 選択肢 | 長所 | 短所 |
|---|---|---|
| backend のジョブが ai-service を呼び、backend が保存する（採用） | スキーマ（Flyway）と書き込みが backend にそろう。ADR-0010 のジョブの仕組みを使い回せる | backend のジョブに、ai-service への呼び出しが加わる |
| Python の ingest-worker が DB に直接書く（当初の構想） | Python だけで完結する | DB に書くサービスが2つになり、スキーマの変更を両方で合わせる必要がある。サービスがさらに1つ増える |

## 結果（Consequences）

### 良い影響

- 権限の判定は今までどおり backend の1か所だけ。ai-service に不具合があっても、権限で見えない文書の内容を返すことはない（そもそも受け取っていない）。
- ai-service は「文章 → ベクトル」「文脈 → 回答文」の関数の集まりになり、DB なしで単体テストできる。
- ai-service が止まっても、検索はキーワード検索だけで動き続ける。

### 悪い影響・受け入れるリスク

- 検索のたびに、質問文をベクトルにするための往復（ローカルの Ollama で数十〜数百ミリ秒）が増える。タイムアウトを短くし、キーワード検索だけに切り替えて吸収する。
- 言語が Java・TypeScript・Python の3つになり、CI と依存の更新（Dependabot）の対象が増える。
- ARCHITECTURE.md の当初の構想（ai-service が DB を読む、ingest-worker を置く）とは形が変わる。ARCHITECTURE.md は、この ADR に合わせて書き直す。

## 検証方法（Compliance）

- ai-service の設定・コードに DB の接続情報やドライバーが入っていないことを、レビューと依存の一覧（`pyproject.toml`）で確かめる。
- ai-service を止めた状態で、backend の検索がキーワード検索だけで結果を返すことを結合テストで確かめる。
- トークンなし・違うトークンの要求を ai-service が断ることを pytest で確かめる。

## 見直す条件（Revisit when）

- RAG（v0.5.0）で、ai-service の中で検索をやり直す必要が出たとき（そのときも権限で絞った ID の集合を backend から渡す形を守る）。
- PDF の抽出で、段組み・表・OCR など PDFBox で扱えないものが必要になったとき（解析を ai-service に移すかを判断する）。
- 通信の往復が検索の応答時間の目標を超えるようになったとき。
