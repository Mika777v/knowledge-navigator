# 動かして確かめる手順（Codespaces・手元のパソコン）

社内規程ナビを起動して、サンプルデータ（架空商事株式会社）で画面を確かめる手順です。
コマンドは README の「ローカルで動かす」と同じで、ここでは準備・確認の順番・困ったときの対処を補います。

## 1. 環境を用意する

### GitHub Codespaces（おすすめ）

1. リポジトリのページで、緑の「Code」→「Codespaces」タブ→「Create codespace on main」の右の「…」→「New with options...」
2. 「Machine type」で **4-core** を選び、「Create codespace」
   - リポジトリの設定（`.devcontainer/devcontainer.json`）で、Docker・Java 21・Node.js 22・Python が入った状態で開きます
   - 2コアだと、最初のビルドに10分以上かかることがあります
3. 開いたら、下のターミナルで次へ進みます

> 無料枠はコア時間で数えます（4コアなら1時間で4コア時間）。使い終わったら https://github.com/codespaces の「…」→「Stop codespace」で止めてください。

### 手元のパソコン

- Docker Desktop（メモリ 4GB 以上を割り当てる）と make を用意します
  - Mac: ターミナルで `xcode-select --install`
  - Windows: WSL2（Ubuntu）の中で作業し、`sudo apt install make git`
- `git clone https://github.com/Mika777v/knowledge-navigator.git` → `cd knowledge-navigator`

## 2. 起動してサンプルデータを入れる

```bash
make up      # 初回はビルドに数分〜10分ほど
make seed    # サンプルデータを入れる（何回実行してもよい。データを追加・変更した PR の後はもう一度）
```

- Codespaces では、起動すると「ポート」タブに **3000（画面）** が出ます。地球儀のアイコンから開きます（公開範囲は Private のままでよい）
- 手元のパソコンでは http://localhost:3000 を開きます

## 3. 確かめる

ログイン: `sato@example.com` / `demo-pass-2026`（デモユーザーは全員同じパスワード。一覧は README）

| 確かめること | 操作 |
|---|---|
| 検索 | 「結婚したら何か制度ある？」で検索し、制度・規程・FAQ・窓口の4つが出る |
| 条文へ移動 | 検索結果の「第5条（結婚祝金）」を押すと、その条まで移動して言葉が強調される |
| 規程の一覧 | ホームの「規程の一覧」→「お金」などのタグで絞り込める |
| FAQ の一覧 | 検索結果の下の「よくある質問をすべて見る」から、カテゴリで絞り込める |
| 権限のちがい | 田中さん（`tanaka@example.com`、管理職）で同じ質問を検索すると、管理職向けの FAQ が増える |
| 管理画面 | 伊藤さん（`ito@example.com`、総務部のコンテンツ担当者）でヘッダーの「管理」から規程・FAQ を編集できる |

API の仕様は 8080 番の `/swagger-ui.html` で見られます（Codespaces では「ポート」タブで 8080 を開く）。

## 4. 止める

```bash
make down    # 止める（データは残る）
make clean   # 止めて、データも消す
```

Codespaces は、しばらく操作しないと自動で止まります。再開したら `make up` だけで元に戻ります（`make seed` はやり直さなくてよい）。

## 困ったとき

| 症状 | 対処 |
|---|---|
| ポートが使われていて起動しない（手元） | `.env` の `FRONTEND_PORT`・`BACKEND_PORT`・`POSTGRES_PORT`・`STORAGE_PORT` を空いている番号にする |
| 起動したか分からない | `make ps` で状態、`make logs` でログを見る（Ctrl+C で終わる） |
| ログインできない | `make seed` を実行したか確かめる。パスワードは `.env` の `DEMO_USER_PASSWORD` |
| 画面が古いまま | PR を取り込んだ後は `make up` をもう一度（変わったところだけビルドし直す） |
| ディスクがいっぱい | `docker system prune` で使っていないイメージを消す |
