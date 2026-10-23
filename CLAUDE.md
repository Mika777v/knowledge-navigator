# CLAUDE.md — このリポジトリでの作業の進め方

社内規程ナビ（Knowledge Navigator）。架空企業「架空商事株式会社」のダミーデータだけを使うポートフォリオ用のリポジトリ。
実在の企業・規程・人物・連絡先を入れない。

## やりとり

- 説明は**日本語で、専門用語をかみくだいて**書く。オーナーは git の操作に詳しくない前提で、手順は画面の操作まで具体的に書く
- 確認したこと・確認できなかったことを分けて報告する（推測で「動きます」と言わない）

## PR の進め方（オーナーと決めたルール）

1. **止めずに PR を作り続ける。** 1件ずつ承認を待たない
2. **PR を5件作ったら止まって報告する。** 報告は、PR の一覧（リンク付き）・それぞれの要点・CI の結果・次にやることを日本語でまとめる
3. **マージはしない。** マージはオーナーが行う。「マージしたので再開して」で次の5件に進む
4. **マージされていない PR に依存する作業は、マージを確認してから始める。** 待つ間は依存しない作業を先に進める
5. **同時に出す PR どうしが衝突しないようにする。** 同じファイル・同じ箇所を触らないよう分け、報告の前に全部を手元で重ねてマージできるか確かめる（`git checkout --detach main` してから順に `git merge`）。`frontend/src/lib/api/schema.d.ts` の作り直しは、同時に出す PR のうち1件だけにする
6. **タグ・GitHub Release は、作る前に必ず確認する。** なお、この開発環境からはタグの push と Release の作成ができないため、オーナーが GitHub の画面で作る（手順と本文を用意して渡す）
7. PR の本文は `.github/pull_request_template.md` の見出しに沿って書く。タイトルは Conventional Commits（`pr-title.yml` の type・scope だけを使う。`e2e` などは scope にない）
8. `gh pr create` はこの環境では使えないため、`gh api repos/Mika777v/knowledge-navigator/pulls` で作る

## Issue の使い方

- **何を作るかは設計書（下の表）で決める。** Issue は進み具合の記録として使う
- Epic（`[Epic] E11 PDF取込・全文検索` など。番号は `docs/TASKS.md` の E01〜E16）に作業を結び付ける
  - PR の本文に、対応する Task の Issue があれば `Closes #番号`、無ければ Epic を `Part of #番号` と書く
  - Task を終えたら、Epic 本文のチェックリストにチェックを付け、PR 番号を書き添える
- マージ済みなのに開いたままの Issue を見つけたら、対応した PR をコメントに書いて閉じる（閉じる前にオーナーに確認する）

## 設計するときに見るもの

| 何を決めるか | 見るもの |
|---|---|
| 何をどの順で作るか | `docs/ROADMAP.md`（版ごとの目標）・`docs/TASKS.md`（作業の番号。PR の範囲はこれに合わせる） |
| 要件・権限のルール | `docs/PRD_KNOWLEDGE_NAVIGATOR.md` |
| 構成・API・検索・セキュリティ | `docs/ARCHITECTURE.md`・`docs/DOMAIN_MODEL.md` |
| 過去の判断 | `docs/adr/`。とくに ADR-0004（権限は検索の前に SQL で絞る。件数からも見えないものを漏らさない）と ADR-0005（画面と API を同じオリジンにする） |
| 書き方 | 同じ種類の既存のコード（楽観ロックの `If-Match`、`ApiErrors`、`AccessPolicy` など）にそろえる |

- 後から変えにくい判断（データの置き場所・方式の選択など）は、作る前に ADR にして PR にする。番号は `docs/adr/README.md` の予約を避けて連番
- DBMS は PostgreSQL のまま変えない

## 依存ライブラリ・外部の部品

- 新しく入れる・上げるときは、**公開から2週間以上たった版**を選ぶ（Maven Central・npm・Docker Hub で公開日を確かめる）
- **保守が続いているか**も確かめる（例: MinIO はコミュニティ版のイメージ配布が止まったため SeaweedFS にした）
- サードパーティの GitHub Actions はコミットのハッシュで固定する

## PR を出す前の確認

- backend: `cd backend && ./gradlew build`（テストとモジュール境界の検査を含む）
- frontend: `cd frontend && pnpm typecheck && pnpm lint && pnpm test`
- 画面に関わる変更は E2E（`frontend/e2e/`）を追加し、手元で起動して全件流す
- 検索に関わる変更は `make eval`（基準値は `data/eval/baseline.json`）
- ダミーデータを変えたら `python3 scripts/data/validate_seed.py`

## 気をつけること（過去につまずいた点）

- **API の DTO の record 名は、モジュールをまたいで重ならないようにする。** OpenAPI のスキーマ名はクラス名で決まり、重なると frontend の生成型が壊れる（例: `OrgContactView`・`DirectoryContact`）
- **テストのユーザーは users テーブルに実際に登録する。** セッションのユーザーはリクエストごとに DB と照合される（存在しない ID だと 401）
- `@Transactional` のテストで JdbcClient で直接更新したら、`EntityManager.clear()` で JPA のキャッシュを捨てる
- テキストブロック（`"""`）は行末の空白を削るので、SQL を連結するときは注意する
- 手元で backend の jar を起動したまま `./gradlew build` すると jar が上書きされて落ちる。ビルドの後に起動し直す
- 確認用の画面・データ投入: `make up` → `make seed`（Codespaces でも同じ。4コアを推奨）
