# ADR-0001: バックエンドは Spring Boot のモジュラーモノリスで構築する

- ステータス: Accepted
- 日付: 2026-10-20
- 関連 Issue / PR: #22
- 改訂: 2026-10-20 実装開始時点の最新安定版に合わせ、Spring Boot のメジャー版を 3 → 4 に修正（判断内容は変更なし、#25）
- 関連ドキュメント: [ARCHITECTURE.md §1-2](../ARCHITECTURE.md), [TECH_STACK_DECISION.md D-01, D-02](../TECH_STACK_DECISION.md), [TECH_STACK_COMPARISON.md §1](../TECH_STACK_COMPARISON.md)

## 背景（Context）

- 本プロダクトの複雑さの中心は AI ではなく、**認可とドメインルール**にある。
  - 所管部署による編集制限、4段階の公開範囲（INTERNAL / MANAGERS / RESTRICTED / CONFIDENTIAL）
  - 文書の版管理と施行日、「制度の公開範囲 ≦ 根拠文書の公開範囲」といった不変条件
  - 窓口の導出ルール（必ず1件以上返す）
- 開発体制は1人（週15〜20時間）で、6か月で Phase1〜5 まで進める。
- 境界づけられたコンテキストは7つ（Content / Organization / Navigation / Ingestion / Intelligence / Insight / Access）あるが、データ量は小さく、独立してスケールさせる必要のあるものは AI 処理だけ。
- 日本の業務システム・社内システムでの実務適用性と、求人市場での評価も選定基準に含める。

## 決定（Decision）

1. バックエンドのコア（認証・認可・ドメイン・検索のオーケストレーション）は **Spring Boot 4 / Java 21 の単一アプリケーション** とする。
2. 内部はコンテキストごとのパッケージ（`organization`, `content`, `navigation`, `ingestion`, `insight`, `audit`, `assistant`, `shared`）に分け、各パッケージを `api / application / domain / infrastructure` の層で構成する。
3. モジュール境界は **Spring Modulith** でテストにより検証する（循環依存・内部パッケージへの直接参照を禁止）。
4. AI 処理（Embedding・RAG・PDF解析）は Phase4 で Python（FastAPI）の別サービスに切り出す（詳細は将来の ADR-0006）。それまではサービスを増やさない。

## 検討した選択肢（Options）

| 選択肢 | 長所 | 短所 |
|---|---|---|
| **A. Spring Boot モジュラーモノリス（採用）** | Spring Security による堅い認可、静的型、トランザクション整合が容易、国内実務での実績が多い | 学習コストが高い、FastAPI より開発速度は遅い |
| B. FastAPI 単体 | 開発が速い、AI ライブラリと同一言語 | 認可・監査・トランザクション境界を自前で組む部分が多く、規模が大きくなると規律の維持が難しい |
| C. 最初からマイクロサービス | 独立デプロイ、技術選択の自由 | 1人開発には運用・分散トランザクション・認可伝播のコストが過大。境界が固まる前に分けると誤った分割になりやすい |
| D. NestJS | フロントと言語を統一できる | AI 親和性・国内業務系の実績ともに中間で、決め手がない |

## 結果（Consequences）

### 良い影響

- 認可ロジック（`AccessPolicy`）を1か所に集約でき、検索・閲覧・API の全経路で同じ判定を使える（ADR-0004）。
- 1つの DB トランザクションで「文書の公開 + セクション生成 + 監査ログ」を完結できる。
- デプロイ単位が1つなので、`docker compose up` だけでデモ環境が動く。
- Modulith のテストにより、将来サービスを分けるときの境界が実際に守られていることを示せる。

### 悪い影響・受け入れるリスク

- Phase4 以降は Java と Python の2言語を保守することになる。
- モジュール境界の規律が崩れると「大きな泥団子」になる → Modulith の検証を CI で必須にして防ぐ。
- 単一プロセスなので、検索負荷が高い時に管理画面も巻き込まれる（PoC 規模では許容）。

## 検証方法（Compliance）

- `ApplicationModules.of(Application.class).verify()` を CI（`ci-backend.yml`）で実行する。
- モジュール間の公開 API は各モジュール直下のパッケージのみとし、`internal` パッケージへの参照はテストで失敗させる。

## 見直す条件（Revisit when）

- 特定モジュール（例: 検索）だけ独立してスケールさせる必要が出たとき。
- 開発者が複数チームに分かれ、デプロイの独立性が必要になったとき。
- 組織の標準が単一言語（Java のみ）で、Spring AI で AI 処理まで賄う方針に変わったとき（ADR-0006 を見直す）。
