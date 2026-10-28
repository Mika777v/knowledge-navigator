# 検索品質の評価用データ

ナビゲーション検索（`GET /api/v1/navigate`、#35）の品質を、同じ質問セットで継続的に測るためのデータ。
検索ロジック・辞書・正規化を変えたときに、良くなったのか悪くなったのかを数値で比べる（docs/ARCHITECTURE.md §4.4）。

> データはすべて架空企業「架空商事株式会社」のもの（data/README.md）。

## questions.yaml

| 項目 | 内容 |
|---|---|
| `id` | 質問の ID（Q001〜） |
| `q` | 利用者が入力しそうな言い方。口語・制度名を知らない言い方・略語・表記ゆれを含める |
| `expect.programs` / `documents` / `faqs` | 正解とみなす案内先のコード。**いずれか1つ**が上位に入れば正解 |
| `expect.contacts` | 窓口のコード。窓口ブロックに**いずれか1つ**が含まれれば正解 |
| `as` | 検索するユーザー（省略時は一般社員 `sato@example.com`）。管理職限定の制度は管理職で評価する |
| `fallback` | `true` のとき、該当がなくても総合窓口（`HR-REP`）が案内されることを確認する |
| `tags` | 集計の切り口 |

### タグ

| タグ | 意味 | 例 |
|---|---|---|
| `life-event` | 状況から探す | 結婚したら何か制度ある？ |
| `no-name` | 制度名を知らない | 入籍したんだけどお祝い金もらえる？ |
| `colloquial` | 口語 | PC壊れた |
| `abbreviation` | 略語 | 育休・産休・有給・時短 |
| `variant` | 表記ゆれ・言い換え | テレワーク / リモートワーク、ＰＣ（全角） |
| `name` / `document-name` | 制度名・文書名を知っている | 通勤手当はどう申請する？ |
| `manager` | 管理職向け | 部下から妊娠の報告を受けた |
| `no-answer` | 該当なし（総合窓口を案内すべき） | 社員食堂のメニュー |
| `paraphrase` | 言い換え（`paraphrase.yaml` だけで使う） | 籍を入れることになりました |

## 指標（#39 の評価バッチで算出）

| 指標 | 定義 |
|---|---|
| Hit@1 | 制度・文書・FAQ のいずれかのブロックで、1位に正解が入った質問の割合 |
| Hit@5 | 制度・文書・FAQ のいずれかのブロックで、上位5件に正解が入った質問の割合 |
| MRR | 正解の最上位の順位の逆数の平均（正解がなければ 0） |
| 窓口正答率 | `expect.contacts` のいずれかが窓口ブロックに含まれた割合 |
| ゼロヒット率 | 制度・文書・FAQ がすべて0件だった質問の割合（`no-answer` を除く） |

タグ別にも集計し、どの種類の質問に弱いかを見る（例: `variant` が低ければ類義語辞書を強化する）。

## 評価の実行（#39）

```bash
make up && make seed   # 全サービスを起動してダミーデータを投入
make eval              # 評価して結果を表示（build/eval/report.md・result.json にも出力）
```

- 評価バッチは `scripts/eval/run_eval.py`。質問ごとに `as` のユーザーでログインし、`GET /api/v1/navigate` を呼ぶ
- CI（ci-compose の `search-eval` ステップ）でも PR ごとに実行し、結果をジョブのサマリーに出す
  - **失敗**: Hit@5 が目標（v0.3.0 の 85%。MVP では 70%）を下回った、窓口が1件もない結果があった、検索に失敗した
  - **警告**: 基準値 `baseline.json` より悪化した指標がある（失敗にはしない）
- 検索を改善して指標が上がったら、`python3 scripts/eval/run_eval.py --update-baseline` で基準値を更新し、同じ PR に含める

## 言い換えの質問セット（E13-T7）

`paraphrase.yaml` は、制度名・キーワード・類義語辞書・FAQ の言い方を**使わずに**同じことを聞く質問（「入籍」ではなく「籍を入れる」、「在宅勤務」ではなく「自宅から働きたい」）。キーワード検索だけでは当たりにくく、v0.4.0 の意味検索（[ADR-0007](../../docs/adr/0007-pgvector.md)）の効果を測るのに使う。

```bash
make up && make seed
make eval-paraphrase   # build/eval-paraphrase/ に出力。Hit@5 が低くても失敗にはしない
```

- 書き方は `questions.yaml` と同じ。ID は `P001`〜、タグには必ず `paraphrase` を付ける（`validate_seed.py` が検査する）
- 質問を足すときは、`data/seed` の keywords・synonyms・faqs の言い方と重ならないように書く。キーワード検索で当たるようにするために、辞書へ語を足すこともしない（意味検索の効果が測れなくなる）
- 基準値 `baseline-paraphrase.json` は**キーワード検索だけ**の成績。v0.4.0 の目標は、意味検索をオンにしたとき Hit@5 がこれより 10 ポイント上がること（ROADMAP）
- CI（ci-compose）でも `questions.yaml` の評価の後に実行し、結果をジョブのサマリーに出す

## 意味検索をオンにしたときの評価（E13-T7）

意味検索（ハイブリッド検索。ADR-0007）は CI ではモデルを動かせないため、手元で測る。

```bash
make up-ai       # ai-service・Ollama も起動し、意味検索をオンにする（初回は bge-m3 の取り寄せに数分。約 1.2GB）
make seed        # ダミーデータを入れる
make eval-vector # ベクトルの合わせ（1 分ごと）が終わるのを待ってから、2つの質問セットで評価する
```

- 結果は `build/eval-vector/report.md`（100問）と `build/eval-paraphrase-vector/report.md`（言い換え37問）に出る。表の「基準値」は、キーワード検索だけの成績
- 見るところ
  - 言い換え37問の Hit@5 が **34.3% 以上**（基準値 24.3% より +10 ポイント。v0.4.0 の目標）
  - 100問の Hit@5 が **85% 以上**のままで、基準値より大きく下がっていないこと。「該当なし」（`no-answer`）の質問で総合窓口が案内されること
- 外れた質問は report.md の「外れた質問」に出る。似ていないものを除く値（`application.yaml` の `knavi.search.vector.min-similarity`、既定 0.5）を変えて測り直すときは、`.env` に `KNAVI_SEARCH_VECTOR_MIN_SIMILARITY=0.6` のように書いて `make up-ai` し直す
- ベクトルの合わせが終わらないときは `docker compose logs backend ai-service ollama` を確かめる

v0.4.0 での結果（2026-10-28、Codespaces 4コア・CPU。条215・制度39・FAQ 50 のベクトル304件）:

| 似ているとみなす基準の値 | 言い換え37問 Hit@5 | 言い換え37問 MRR | 100問 Hit@1 | 100問 Hit@5 | 100問 MRR |
|---|---|---|---|---|---|
| キーワード検索だけ（基準値） | 24.3% | 0.203 | 91% | 100% | 0.947 |
| 0.5（既定） | **81.1%** | 0.634 | 87% | 99% | 0.921 |
| 0.6 | 40.5% | 0.322 | 90% | 99% | 0.938 |

0.6 にすると100問はわずかに戻るが、言い換えの効果が半分になるため、0.5 のままにした。

## 重みの調整（E12-T4）

検索スコアの重み（`application.yaml` の `knavi.navigation.weights`、docs/ARCHITECTURE.md §4.3）は、この質問セットでグリッド探索して決める。

```bash
make up && make seed
make eval-tune                                                   # 既定のグリッド（辞書・制度名・FAQ の質問文の重み）
make eval-tune ARGS="--grid heading=1.0,1.5,2.0 --grid synonym=0.6,0.8,1.0"   # 調べる重みと値を指定する
```

- 組み合わせごとに backend の jar を起動し直して評価し、Hit@5 → MRR → Hit@1 の順に良いものを `build/eval/tuning.md` に並べる
- 同じ点数の組み合わせが多いときは、今の値に近いものを選ぶ（質問セットに合わせすぎないように）
- 重みを変えたら、`make eval` の結果と探索の表を PR に書き、`--update-baseline` で基準値も更新する

## RAG の質問セット（E14-T6）

`rag.yaml` は、根拠付き回答（v0.5.0、[ADR-0013](../../docs/adr/0013-rag-generation-model-and-delivery.md)）を測るための質問セット（R001〜）。検索ではなく、**回答文**が正しい条を引用し、条文の数値を写し間違えていないかを見る。

| 項目 | 内容 |
|---|---|
| `id` | 質問の ID（R001〜） |
| `q` | 質問文。答えが規程の1〜2条に書いてあるもの |
| `answerable` | `false` のとき、規程に記載がなく「回答できない」と答えるべき質問（省略時は `true`） |
| `expect.sections` | 引用してほしい条（`{document: 文書のコード, article: 第N条}`）。**いずれか1つ**を引用すれば正解 |
| `expect.facts` | 回答に含まれるべき数値・語（例: `30,000円`）。`expect.sections` のどれかの本文に、同じ書き方で書かれていること |
| `as` | 質問するユーザー（省略時は一般社員 `sato@example.com`） |
| `tags` | 集計の切り口 |

### タグ

| タグ | 意味 | 例 |
|---|---|---|
| `amount` | 金額 | 結婚祝金はいくら？ |
| `days` | 日数・期間・回数 | 結婚休暇は何日？ |
| `deadline` | 期限 | 出張の精算はいつまで？ |
| `condition` | 対象者・条件 | 契約社員でも対象？ |
| `procedure` | 手続き・時期 | いつ振り込まれる？ |
| `no-answer` | 規程に記載がない（回答しないのが正解） | 社員食堂のメニュー |

### 指標（評価バッチは E14-T6 の後半で作る）

| 指標 | 定義 |
|---|---|
| 引用正確性 | 回答できた質問のうち、引用に `expect.sections` の条が1つ以上含まれる割合（目標 95% 以上、docs/ROADMAP.md M6） |
| 要点の正答率 | 回答できた質問のうち、`expect.facts` がすべて回答文に含まれる割合 |
| 回答率 | `answerable: true` の質問のうち、回答できた（`unanswerable` にならなかった）割合 |
| 回答不能の適切さ | `answerable: false` の質問のうち、`unanswerable` になった割合 |
| 回答までの時間 | 質問から回答（または `unanswerable`）までの時間の中央値・最大 |

- 回答文の忠実さ（言い換えた内容が根拠と合っているか）は、機械では測りきれないため、抜き取りで人が確かめる（docs/ARCHITECTURE.md §5.7）
- `validate_seed.py` は、条が存在することと、`expect.facts` が条の本文にあることを検査する

## 質問を追加するとき

- 検索で期待どおりの結果が出なかった質問は、Issue（Search Quality テンプレート）を作り、ここに追加する
- 正解のコードは `python3 scripts/data/validate_seed.py` で存在を検査する
- 目標値は docs/PRODUCT_VISION.md §6（MVP で Hit@5 ≥ 70%、Phase3 で 85%）
