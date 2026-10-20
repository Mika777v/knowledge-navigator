# data — ダミーデータ

> ⚠️ ここにあるデータはすべて **架空企業「架空商事株式会社」** のもので、実在の企業・規程・人物・連絡先とは関係ありません。
> ライセンス: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/deed.ja)

## 構成

```
data/
├── seed/                 # アプリに投入する初期データ（`make seed`、#33 で実装）
│   ├── departments.yaml  # 部署（8部署 + 課3）
│   ├── contacts.yaml     # 問い合わせ窓口（15）
│   ├── users.yaml        # デモユーザー（全ロール）
│   └── documents/        # 架空の社内規程（#29）
└── eval/                 # 検索品質の評価用質問セット（#39）
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

パスワードは seed に書かず、投入時に環境変数 `DEMO_USER_PASSWORD` の値を全員に設定する（ローカルの既定値は `.env.example` に記載予定、#33）。

## ルール（`scripts/data/validate_seed.py` で検査）

- メールアドレス・URL は `example.com`（予約済みドメイン）のみ。電話は4桁の架空の内線番号のみ
- 部署・窓口・ユーザーの参照先が存在すること、コードが重複しないこと
- 窓口を持つ部署には代表窓口（`is_primary: true`）がちょうど1つあること（窓口を導出できないときのフォールバック先）
- 全ロールのデモユーザーがいること、パスワードを書かないこと

```bash
python3 scripts/data/validate_seed.py
```

## 規程本文を書くときの注意（#29）

- 実在企業の規程を転載しない
- 厚生労働省のモデル就業規則など公的な資料を参考にする場合は、独自に書き起こしたうえで出典を明記する
- 金額・日数などは架空の値でよいが、法令の最低基準を下回る記載はしない（誤解を招くため）
