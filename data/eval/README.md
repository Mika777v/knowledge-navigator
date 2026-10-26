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

## 質問を追加するとき

- 検索で期待どおりの結果が出なかった質問は、Issue（Search Quality テンプレート）を作り、ここに追加する
- 正解のコードは `python3 scripts/data/validate_seed.py` で存在を検査する
- 目標値は docs/PRODUCT_VISION.md §6（MVP で Hit@5 ≥ 70%、Phase3 で 85%）
