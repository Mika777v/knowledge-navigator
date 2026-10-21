# 社内規程ナビ（Knowledge Navigator）

> 「何を調べればいいかわからない」を、「誰に・何を・どこで」まで案内する。

社内規程・福利厚生・手順書・FAQ・組織情報を横断し、**自然言語の質問**から
**使える制度 / 根拠となる規程の条文 / よくある質問 / 問い合わせ先** をまとめて案内するナビゲーションシステムです。

> ⚠️ 本リポジトリのデータはすべて架空企業「架空商事株式会社」のダミーデータです。実在の企業・規程・人物とは関係ありません。

![検索結果](docs/images/search-results.png)

## できること

| 利用者 | 機能 |
|---|---|
| 社員 | 「結婚したら何か制度ある？」「PC壊れた」のような言い方で検索し、**制度・規程・FAQ・窓口の4ブロック**で案内を受ける。制度名を知らなくても、きっかけ（結婚・出産・引越し・介護など）から探せる |
| 社員 | 制度の詳細（対象者・金額・期限・申請方法・根拠の条文・窓口）、規程の全文（目次・条文へのリンク・検索語の強調・過去の版）、FAQ を読む |
| 社員 | 検索結果が役に立ったかを送る（検索の改善に使う） |
| コンテンツ担当者 | キーワード辞書（「入籍」→ きっかけ「結婚」など）を画面から編集する。何も見つからなかった質問・よく検索される質問・フィードバックを見て改善する |
| システム管理者 | 監査ログ（誰が・いつ・何を変更したか）を確認する |

**見えてはいけないものは見せない**: 規程・制度・FAQ には公開範囲（社内公開・管理職限定・部署限定・機密）があり、一覧・詳細・関連・検索のすべてで、利用者が見てよいものだけを SQL の段階で絞り込みます（[ADR-0004](docs/adr/0004-permission-prefilter.md)）。件数や抜粋から存在が漏れないことを、ロール × 公開範囲 × 経路のテストで確かめています。

<table>
<tr>
<td><img src="docs/images/home.png" alt="ホーム" width="420"></td>
<td><img src="docs/images/document-markdown.png" alt="規程の詳細" width="420"></td>
</tr>
<tr>
<td><img src="docs/images/admin-dictionary.png" alt="キーワード辞書" width="420"></td>
<td><img src="docs/images/admin-analytics.png" alt="検索の分析" width="420"></td>
</tr>
</table>

## ローカルで動かす

必要なもの: Docker（Compose v2）、make。`make test` には Java 21 と Node.js 22 + pnpm、`make eval` には Python 3 と PyYAML も必要です。

```bash
make up      # .env が無ければ .env.example から作成し、ビルドして起動
make seed    # ダミーデータを投入（デモユーザーでログインできるようになる）
make eval    # 検索品質の評価（評価用質問50問）
make test    # テスト（backend・frontend）
make down    # 停止（データは残る）
make clean   # 停止してデータも削除
make help    # コマンド一覧
```

http://localhost:3000 を開き、下のデモユーザーでログインします。パスワードは `.env` の `DEMO_USER_PASSWORD`（`.env.example` の既定値は `demo-pass-2026`）で、全員共通です。

| メールアドレス | 名前 | 所属 | ロール | 想定 |
|---|---|---|---|---|
| `sato@example.com` | 佐藤 太郎 | 東日本営業課 | 一般社員 | 入社3年目・結婚予定 |
| `suzuki@example.com` | 鈴木 美咲 | 開発第一課 | 一般社員 | 新入社員（入社1か月） |
| `yamamoto@example.com` | 山本 さくら | 西日本営業課 | 一般社員 | 契約社員（制度の対象者の違いを確認する） |
| `tanaka@example.com` | 田中 健一 | 営業部 | 管理職 | 管理職向けの FAQ が見える |
| `takahashi@example.com` | 高橋 由美 | 人事部 | コンテンツ担当者 | 人事の規程・制度・FAQ を管理 |
| `ito@example.com` | 伊藤 誠 | 総務部 | コンテンツ担当者 | 慶弔・社内ルールを管理 |
| `kobayashi@example.com` | 小林 修 | 法務・コンプライアンス部 | コンテンツ担当者 | ハラスメント防止規程を管理 |
| `kato@example.com` | 加藤 直樹 | 情報システム部 | コンテンツ担当者 | 情報セキュリティ・PC 貸与規程を管理 |
| `watanabe@example.com` | 渡辺 翔 | 情報システム部 | システム管理者 | 監査ログを確認（職務分離のためコンテンツは編集できない） |

| サービス | URL |
|---|---|
| frontend | http://localhost:3000 |
| backend | http://localhost:8080/actuator/health 、API 仕様 http://localhost:8080/swagger-ui.html |
| PostgreSQL | localhost:5432（pg_bigm / pgvector 入り） |

## 構成

```mermaid
flowchart LR
  B[ブラウザ] -->|HTTPS / Cookie| F["Next.js 16<br/>画面 + BFF（/api/*）"]
  F -->|REST + セッション Cookie| S["Spring Boot 4<br/>（Spring Modulith）"]
  S --> P[("PostgreSQL 17<br/>pg_bigm / pgvector")]
  subgraph backend モジュール
    direction TB
    O[organization<br/>認証・組織・窓口]
    C[content<br/>規程・制度・FAQ]
    N[navigation<br/>検索・辞書]
    I[insight<br/>検索ログ・分析]
    A[audit<br/>監査ログ]
  end
  S -.- O & C & N & I & A
```

- **検索**: 質問の整理（口語の除去・語の抽出）→ キーワード辞書（最長一致）→ pg_bigm の全文検索 → スコアの統合 → 窓口の導出（必ず1件以上）。詳しくは [ARCHITECTURE.md §4](docs/ARCHITECTURE.md)
- **認可**: `AccessPolicy` に集約し、すべての読み取りを SQL の WHERE 句で絞り込む（プレフィルタ）
- **ログイン**: セッション Cookie + CSRF トークン。同じアカウントで続けて失敗すると一時的にロックする
- **検索ログ**: ユーザー ID は HMAC で仮名化。分析では2人以上が検索した質問だけを集計し、保存期間（既定180日）を過ぎたら自動で削除する

## 品質

評価用質問50問（[data/eval](data/eval/)）で、PR ごとに CI で検索品質を測っています。目標（Hit@5 70%）を下回ると CI が失敗します。

| 指標 | 現在の値 |
|---|---|
| Hit@1（1位に正解） | 92% |
| Hit@5（上位5件に正解） | 100% |
| MRR | 0.96 |
| 窓口正答率 | 100% |
| ゼロヒット率 | 0% |

- backend: JUnit + Testcontainers（実際の PostgreSQL + pg_bigm）、Spring Modulith のモジュール境界テスト、権限マトリクステスト
- frontend: Vitest + Testing Library、E2E は Playwright（`make up` した環境をブラウザで操作し、CI でも実行）
- セキュリティ: CSRF 対策、CSP などのセキュリティヘッダー、Markdown は HTML を描画しない、監査ログは追記専用（DB のトリガーで更新・削除を拒否）

## 技術スタック

| 層 | 技術 |
|---|---|
| frontend | Next.js 16（App Router）/ React 19 / Tailwind CSS 4 / TanStack Query / openapi-typescript |
| backend | Spring Boot 4 / Java 21 / Spring Modulith / Spring Security / Flyway |
| DB | PostgreSQL 17 + pg_bigm（日本語の部分一致検索）+ pgvector（Phase4 の意味検索用） |
| CI | GitHub Actions（変更に応じて backend・frontend・DB・compose・データ・検索品質を実行） |
| 今後 | FastAPI（PDF 取り込み）/ Ollama・Azure OpenAI・OpenAI（意味検索・根拠付き回答） |

## ドキュメント

- [設計ドキュメント一覧・最終まとめ](docs/README.md)
- [Product Vision](docs/PRODUCT_VISION.md) / [PRD](docs/PRD_KNOWLEDGE_NAVIGATOR.md) / [Architecture](docs/ARCHITECTURE.md) / [ADR](docs/adr/)
- [ロードマップ](docs/ROADMAP.md) / [タスク分解](docs/TASKS.md)
- [GitHub 運用ルール](docs/GITHUB_WORKFLOW.md) / [リポジトリ初期設定の手順](docs/REPOSITORY_SETUP.md)
- [ダミーデータ](data/README.md) / [評価用質問](data/eval/README.md)
- [変更履歴](CHANGELOG.md)

## ステータス

MVP（v0.1.0）を実装中です。検索・閲覧・辞書・分析・監査ログ・FAQ の編集まで動きます。残りは制度・文書・きっかけの編集画面などです（[変更履歴](CHANGELOG.md)）。今後の計画は [ROADMAP](docs/ROADMAP.md) を参照してください。

## ライセンス

コード: [MIT](LICENSE)。ダミーデータ（`data/`）: CC BY 4.0。
