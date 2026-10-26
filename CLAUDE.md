# CLAUDE.md — このリポジトリでの作業の進め方

社内規程ナビ（Knowledge Navigator）。架空企業「架空商事株式会社」のダミーデータだけを使うポートフォリオ用のリポジトリ。
実在の企業・規程・人物・連絡先を入れない。

## やりとり

- 説明は**日本語で、専門用語をかみくだいて**書く。オーナーは git の操作に詳しくない前提で、手順は画面の操作まで具体的に書く
- 確認したこと・確認できなかったことを分けて報告する（推測で「動きます」と言わない）

## PR の進め方（オーナーと決めたルール）

1. **止めずに PR を作り続ける。** 1件ずつ承認を待たない
2. **マージは自動マージに任せる。** PR を作ったら自動マージを予約する（`gh api -X PUT repos/Mika777v/knowledge-navigator/pulls/<番号>/ccr/auto_merge -f merge_method=squash`。このリポジトリはスカッシュマージだけを許可している）。CI（`ci-ok` と `conventional-commits`）が通ると GitHub がスカッシュマージする。CI が落ちたら直して push し直す
3. **次のものは自動マージを予約せず、マージ前にオーナーに確認する。**
   - タグ・GitHub Release
   - DB のマイグレーション（`backend/src/main/resources/db/migration` の追加・変更）
   - 認証・権限まわりの変更（ログイン・セッション・`AccessPolicy`・公開範囲の判定）
   - 依存ライブラリのメジャーバージョンの更新
   - ADR の追加・変更（`docs/adr/`。オーナーが内容を読んで学ぶため。PR の本文に、選択肢と決めた理由を短くまとめる）
4. **区切りまで進めたら止まって報告する。** 目安は**マージ 10〜20 件**、理想は**リリースタグ1つ分**（ROADMAP の版の単位）。報告は、マージした PR の一覧（リンク付き）・それぞれの要点・確認待ちの PR・次にやることを日本語でまとめる
5. **前の PR に依存する作業は、そのマージを確認してから始める。** 待つ間は依存しない作業を先に進める
6. **同時に開いている PR どうしが衝突しないようにする。** 同じファイル・同じ箇所を触らないよう分け、まとめて重ねてマージできるか確かめる（`git checkout --detach main` してから順に `git merge`）。`frontend/src/lib/api/schema.d.ts` の作り直しは、同時に開いている PR のうち1件だけにする
7. **タグ・GitHub Release は、作る前に必ず確認する。** この開発環境からはタグの push と Release の作成ができないため、オーナーが GitHub の画面で作る（手順と本文を用意して渡す）
8. PR の本文は `.github/pull_request_template.md` の見出しに沿って書く。タイトルは Conventional Commits（`pr-title.yml` の type・scope だけを使う。`e2e` などは scope にない）
9. `gh pr create` はこの環境では使えないため、`gh api repos/Mika777v/knowledge-navigator/pulls` で作る
10. **force push（ブランチの強制上書き）はしない。** 許可していない。push 済みのブランチの履歴は書き換えない（rebase・amend・`reset` してからの push をしない）
    - マージ済みの PR を直す・続きをするときは、最新の main から**新しいブランチを切って**別の PR にする（ブランチ名は `docs/...`・`feat/...`・`fix/...` など）
    - まだマージされていない PR を直すときは、同じブランチに新しいコミットを足して push する。main の変更を取り込むときは rebase ではなく merge する

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
- ai-service: `make test-ai`（ruff と pytest。uv が必要）
- 画面に関わる変更は E2E（`frontend/e2e/`）を追加し、手元で起動して全件流す
- 検索に関わる変更は `make eval`（基準値は `data/eval/baseline.json`）
- ダミーデータを変えたら `python3 scripts/data/validate_seed.py`

## トークンの使い方（2026-10 にオーナーと決めたこと）

- **リリースのタグ1つ分ごとに、新しいセッションで始める。** 会話が長いと、呼び出しのたびに読み直す量が増える。続きは ROADMAP・TASKS・Epic のチェックリストからたどる。会話の中で決めたことは、セッションを終える前にこのファイルや docs に書いておく
- 確認のコマンドはまとめて1回で実行する。CI・マージを待つときは、何度も見に行かず、バックグラウンドで待って知らせを受ける
- ツールの出力は必要な行だけにする（ファイル全体・長いログを表示しない。`grep`・`tail`・行範囲の指定を使う）
- PR の本文・報告は、テンプレートの見出しと要点を保ったまま短く書く
- 新しいセッションでは、手元の DB・Docker・辞書のキャッシュは残っていない。`make up` → `make seed` からやり直す（Sudachi の辞書は Gradle が取り寄せる）

## 気をつけること（過去につまずいた点）

- **API の DTO の record 名は、モジュールをまたいで重ならないようにする。** OpenAPI のスキーマ名はクラス名で決まり、重なると frontend の生成型が壊れる（例: `OrgContactView`・`DirectoryContact`）
- **テストのユーザーは users テーブルに実際に登録する。** セッションのユーザーはリクエストごとに DB と照合される（存在しない ID だと 401）
- `@Transactional` のテストで JdbcClient で直接更新したら、`EntityManager.clear()` で JPA のキャッシュを捨てる
- テキストブロック（`"""`）は行末の空白を削るので、SQL を連結するときは注意する
- 手元で backend の jar を起動したまま `./gradlew build` すると jar が上書きされて落ちる。ビルドの後に起動し直す
- 確認用の画面・データ投入: `make up` → `make seed`（Codespaces でも同じ。4コアを推奨）
