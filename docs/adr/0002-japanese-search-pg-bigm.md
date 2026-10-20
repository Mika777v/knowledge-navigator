# ADR-0002: 日本語全文検索に PostgreSQL + pg_bigm を採用する

- ステータス: Accepted
- 日付: 2026-10-20
- 関連 Issue / PR: #22, #23（Docker イメージ）
- 関連ドキュメント: [ARCHITECTURE.md §4](../ARCHITECTURE.md), [TECH_STACK_DECISION.md D-04](../TECH_STACK_DECISION.md), [TECH_STACK_COMPARISON.md §3](../TECH_STACK_COMPARISON.md)

## 背景（Context）

- 検索対象は日本語の規程・FAQ・制度で、「結婚」「育休」のような短い語や、条文の一部での部分一致が重要になる。
- **PostgreSQL 標準の Full Text Search（`to_tsvector`）は日本語を単語に分割しない。** そのままでは「結婚祝金」の中の「結婚」にヒットしないなど、日本語検索に使えない。
- データ規模は文書数百〜数千、セクション（条文）数万〜数十万程度で、専用検索クラスタが必要な規模ではない。
- **権限フィルタを検索と同時に確実に適用すること**が最重要要件（ADR-0004）。検索対象と権限情報が別のストアにあると、同期の遅れがそのまま情報漏えいになる。
- ポートフォリオとして `docker compose up` で誰でも動かせることが必要。

## 決定（Decision）

1. 全文検索は **PostgreSQL 17 + pg_bigm 拡張** で行う。検索用の正規化済みテキスト列（`search_text`）に `gin_bigm_ops` の GIN インデックスを張り、`LIKE '%語%'` と `bigm_similarity()` で検索・スコアリングする。
2. Phase3 で、Sudachi による形態素解析の正規化形を `simple` 構成の `tsvector` 列に追加し、表記ゆれ対策とランキング改善に併用する。
3. 検索処理は `SearchPort` インターフェースの背後に置き、将来 OpenSearch などへ差し替えられるようにする。
4. pg_bigm は公式の postgres イメージに含まれないため、`pgvector/pgvector:pg17` をベースに pg_bigm をビルドした独自イメージ（`infra/docker/postgres/Dockerfile`）を使う。

## 検討した選択肢（Options）

| 選択肢 | 長所 | 短所 |
|---|---|---|
| **A. PostgreSQL + pg_bigm（採用）** | 導入が簡単、部分一致の取りこぼしが少ない、権限条件を同じ SQL の WHERE に書ける、運用対象が DB 1つ | ランキング機能は弱い、2文字未満の語は苦手、インデックスが大きめ |
| B. PostgreSQL 標準 FTS のみ | 追加拡張が不要 | 日本語を分かち書きしないため実用にならない |
| C. アプリ側形態素解析 + tsvector のみ | 正規化形で表記ゆれに強い | 辞書にない語・未知語の部分一致を取りこぼす。単独では不十分（Phase3 で A と併用） |
| D. PGroonga | 日本語全文検索として高機能 | マネージド DB での提供が環境に依存し、移植性に不安 |
| E. Elasticsearch / OpenSearch（kuromoji / sudachi） | 検索機能が最も豊富（BM25、同義語、ハイライト、集計） | クラスタ運用と DB との同期処理が必要。権限情報の同期遅延がリスク。この規模では過剰 |

## 結果（Consequences）

### 良い影響

- 検索・権限フィルタ・マスタ参照を1つの SQL とトランザクションで扱える。
- 権限変更が即座に検索結果へ反映される（インデックスの再同期が不要）。
- Phase4 の pgvector と同じ DB でハイブリッド検索ができる（ADR-0007 予定）。

### 悪い影響・受け入れるリスク

- 検索機能（同義語展開、ハイライト、ファセット集計）はアプリ側で実装する必要がある。
- マネージド PostgreSQL（RDS / Cloud SQL / Azure）で pg_bigm が使えるかは環境ごとに確認が必要。使えない場合は C 単独か D に切り替える。
- 独自 Docker イメージのメンテナンス（PostgreSQL・pg_bigm のバージョン追従）が発生する。

## 検証方法（Compliance）

- 評価用質問セット（`data/eval/questions.yaml`）の Hit@5・ゼロヒット率を CI で計測し、検索変更のたびに比較する（#39）。
- 主要な検索クエリについて `EXPLAIN` で bigm インデックスが使われていることを確認する（#23）。

## 見直す条件（Revisit when）

- セクション数が 100 万件を超え、p95 レイテンシが 500ms を超えたとき。
- 多言語対応や、ファセット集計・高度なハイライトが必要になったとき。
- 本番環境のマネージド DB で pg_bigm が使えないと判明したとき。
- Phase3 のスパイク（E12-T7）で、OpenSearch の評価結果が明確に上回ったとき。
