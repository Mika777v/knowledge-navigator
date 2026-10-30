# data — ダミーデータ

> ⚠️ ここにあるデータはすべて **架空企業「架空商事株式会社」** のもので、実在の企業・規程・人物・連絡先とは関係ありません。
> ライセンス: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/deed.ja)

## 構成

```
data/
├── seed/                 # アプリに投入する初期データ（`make seed`）
│   ├── departments.yaml  # 部署（8部署 + 課3）
│   ├── contacts.yaml     # 問い合わせ窓口（15）
│   ├── users.yaml        # デモユーザー（全ロール）
│   ├── categories.yaml   # カテゴリ（2階層）
│   ├── tags.yaml         # 規程のタグの語彙（対象者・話題）。規程の front matter の tags で使う
│   ├── documents/        # 架空の社内規程（10本、Markdown + front matter）
│   ├── programs.yaml     # 制度（39件。根拠の条・窓口・キーワード）
│   ├── life_events.yaml  # きっかけ（結婚・出産など8件）
│   ├── assistant_questions.yaml  # AI アシスタントの確認質問（ADR-0014）
│   ├── faqs.yaml         # よくある質問（50件。うち2件は管理職限定）
│   └── synonyms.yaml     # 類義語辞書（16グループ。育休 = 育児休業 など）
├── pdf/                  # documents/ の規程を PDF にしたもの（10本。PDF 取り込みの確認・テスト用）
└── eval/                 # 検索品質の評価用質問セット（100問。eval/README.md）
```

## デモユーザー

| ユーザー | 所属 | ロール | 想定 |
|---|---|---|---|
| sato@example.com | 東日本営業課 | EMPLOYEE | 一般社員（入社3年目・結婚予定） |
| suzuki@example.com | 開発第一課 | EMPLOYEE | 新入社員 |
| yamamoto@example.com | 西日本営業課 | EMPLOYEE（契約社員） | 制度の対象者の違いを確認する |
| tanaka@example.com | 営業部 | MANAGER | 管理職 |
| takahashi@example.com | 人事部 | CONTENT_EDITOR | 人事担当 |
| ito@example.com | 総務部 | CONTENT_EDITOR | 総務担当 |
| kobayashi@example.com | 法務・コンプライアンス部 | CONTENT_EDITOR | 法務担当 |
| kato@example.com | 情報システム部 | CONTENT_EDITOR | 情シスのコンテンツ担当 |
| watanabe@example.com | 情報システム部 | SYSTEM_ADMIN | システム管理者 |

パスワードは seed に書かず、投入時に環境変数 `DEMO_USER_PASSWORD` の値を全員に設定する（ローカルの既定値は `.env.example`）。

## 投入（make seed）

```bash
make up     # 先に DB と backend を起動
make seed   # data/seed を投入（何度実行してもよい）
```

- コード（`code` / `slug` / `email`）で既存の行を特定して上書きする。文書は同じ版ラベルの版があれば本文を変更しない（公開済みの版は不変のため。内容を変えるときは `version_label` を上げる）
- 規程は本番と同じ処理で条単位に分割される（backend の `DocumentImport`）
- ナビゲーションキーワードは投入のたびに作り直す（制度名・きっかけ名・`keywords`・窓口の `topics`）
- 総合窓口（窓口を導出できないときの案内先）は `HR-REP`

## 制度・きっかけ・FAQ の書き方

| ファイル | 主な項目 | ルール |
|---|---|---|
| `programs.yaml` | `basis`（文書と条）、`contacts`（窓口と役割 APPLY / INQUIRY / PAYMENT）、`keywords`、`conditions`（対象者の条件。質問のコード → 当てはまる選択肢） | 窓口は1件以上必須。公開範囲は根拠文書より広くしない |
| `life_events.yaml` | `programs`（PRIMARY = まず確認する制度 / RELATED = あわせて必要な手続き）、`keywords` | |
| `assistant_questions.yaml` | `choices`（value と label）、`profile_attribute`（利用者の属性から初めに選ぶ値） | 制度の `conditions` はこの質問と選択肢だけを使う。すべての選択肢を並べた条件は書かない |
| `faqs.yaml` | `links`（program か document + article）、`contact`、`visibility` | 窓口か関連制度のどちらかが必要 |
| `documents/*.md` の `tags` | `tags.yaml` にある名前（対象者 TARGET・話題 TOPIC） | 10個まで。社員が規程の一覧で絞り込むときに使う |

## ルール（`scripts/data/validate_seed.py` で検査）

- メールアドレス・URL は `example.com`（予約済みドメイン）のみ。電話は4桁の架空の内線番号のみ
- 部署・窓口・ユーザーの参照先が存在すること、コードが重複しないこと
- 窓口を持つ部署には代表窓口（`is_primary: true`）がちょうど1つあること（窓口を導出できないときのフォールバック先）
- 全ロールのデモユーザーがいること、パスワードを書かないこと
- 規程: front matter の必須項目と参照先、注記、条番号の連番、規程どうしの条文参照が実在する条を指すこと
- 制度・きっかけ・FAQ: 参照先の文書・条・窓口・制度が存在すること、窓口の有無、公開範囲が根拠文書より広くないこと

```bash
python3 scripts/data/validate_seed.py
```

## 規程（documents/）

| コード | 規程 | 所管 | 条数 |
|---|---|---|---|
| REG-HR-001 | 就業規則 | 人事部 | 45 |
| REG-HR-002 | 育児介護休業規程 | 人事部 | 23 |
| REG-HR-003 | 在宅勤務規程 | 人事部 | 18 |
| REG-HR-004 | 通勤手当規程 | 人事部 | 13 |
| REG-HR-005 | 福利厚生規程 | 人事部 | 18 |
| REG-GA-001 | 慶弔見舞金規程 | 総務部 | 15 |
| REG-FIN-001 | 旅費規程 | 経理部 | 18 |
| REG-IT-001 | 情報セキュリティ規程 | 情報システム部 | 22 |
| REG-IT-002 | PC貸与規程 | 情報システム部 | 15 |
| REG-LEGAL-001 | ハラスメント防止規程 | 法務・コンプライアンス部 | 18 |

書式:

- 先頭の front matter（YAML）に `code` `title` `document_type` `category` `owner_department` `visibility` `version_label` `effective_from` `summary` を書く
- 本文の冒頭に「架空企業『架空商事株式会社』のサンプル」である旨の注記を置く
- 章は `## 第N章 …`、条は `### 第N条（見出し）`（セクション分割 #32 の単位になる）。条番号は第1条からの連番
- 他の規程を参照するときは「就業規則第32条」のように規程名と条番号を続けて書く（参照先の存在を検査する）

## 規程本文を書くときの注意

- 実在企業の規程を転載しない
- 厚生労働省のモデル就業規則など公的な資料を参考にする場合は、独自に書き起こしたうえで出典を明記する
- 金額・日数などは架空の値でよいが、法令の最低基準を下回る記載はしない（誤解を招くため）

## 規程の PDF（pdf/）

`documents/` の Markdown から作った PDF です。PDF 取り込み（v0.2.0）の動作確認とテストに使います。
実際の社内規程の PDF に近づけるため、A4・ヘッダー（規程名）・フッター（ページ番号）付きで組んでいます。条がページをまたぐことや、本文中の条の参照（「第42条）。」など）が折り返しで行頭に来ることもあります。

```bash
make pdfs   # documents/ を変えたら作り直す（reportlab と IPA ゴシックが必要）
```

- フォントは IPA ゴシック（Ubuntu: `fonts-ipafont-gothic`）。PDF には使った文字だけを埋め込む（IPA フォントライセンス v1.0 で認められている）
- 同じ入力・同じフォントからは、同じ PDF ができる（作成日時などを固定）
- `make seed` で、規程の公開中の版に、この PDF を**原本として結び付ける**（取り込みと同じ処理で解析し、条ごとの原本のページも付ける）。規程の詳細で「原本（PDF）を開く」「p.3」を確かめられる
