# ADR-0005: Next.js を画面兼 BFF として使う

- ステータス: Accepted
- 日付: 2026-10-20
- 関連 Issue / PR: #26
- 関連ドキュメント: [TECH_STACK_DECISION.md D-03](../TECH_STACK_DECISION.md), [ARCHITECTURE.md §2](../ARCHITECTURE.md), [frontend/README.md](../../frontend/README.md)

## 背景（Context）

- 認証は backend（Spring Security）のセッション Cookie と CSRF トークンで行う（ARCHITECTURE.md §6）。
- 画面と backend が別オリジンになると、CORS の設定、`SameSite` 属性、サードパーティ Cookie 制限への対応が必要になり、設定ミスが脆弱性につながりやすい。
- Phase5 以降は RAG の回答をストリーミング（SSE）で表示するため、画面側で長時間の応答を中継できる必要がある。
- 社内システムなので SEO は不要。サーバーレンダリングの高度な機能は必須ではない。

## 決定（Decision）

1. 画面は **Next.js（App Router）** で作る。
2. Next.js の **Route Handler を BFF** とし、ブラウザからの `/api/*` を backend の `/api/*` へ転送する（`src/app/api/[...path]/route.ts`）。ブラウザは backend を直接呼ばない。
3. 転送先は実行時の環境変数 `BACKEND_URL` で指定する（`next.config` の rewrites はビルド時に固定されるため使わない）。
4. 転送時の安全策:
   - リクエストヘッダーは許可リスト方式（Cookie・CSRF トークン・Content-Type など）
   - `.` / `..` を含むパスは拒否し、`/api` 以外の backend のパス（actuator など）に到達させない
   - backend に接続できないときは、内部情報を含めない 502（Problem Details）を返す
5. 認可の判断は BFF では行わず、すべて backend が行う（ADR-0004）。
6. Server Actions や ISR などの高度な機能は、必要になるまで使わない。

## 検討した選択肢（Options）

| 選択肢 | 長所 | 短所 |
|---|---|---|
| **A. Next.js + Route Handler の BFF（採用）** | 同一オリジンで Cookie を扱える、SSE の中継が容易、実行時に転送先を変えられる、市場価値が高い | Next.js の学習コスト、Node.js の実行環境が必要 |
| B. Next.js + `rewrites` で転送 | 設定だけで済む | 転送先がビルド時に固定され、イメージを環境ごとに作り直す必要がある。ヘッダーの制御ができない |
| C. React（Vite）SPA + backend へ直接アクセス | 構成が単純、静的配信だけで済む | 別オリジンになり CORS・Cookie の設定が必要。SSE も backend 直結になる |
| D. Spring Boot から静的ファイルとして配信 | デプロイ単位が1つ | フロントの開発体験が落ちる、フロントとバックの技術選択が結合する |

## 結果（Consequences）

### 良い影響

- CORS 設定が不要になり、Cookie は `SameSite=Lax` のまま使える。
- backend をインターネットに直接公開しなくてよい（frontend だけ公開すればよい）。
- 画面ごとに必要なデータを BFF で集約する余地ができる。

### 悪い影響・受け入れるリスク

- リクエストが1段増える（レイテンシの増加。社内システムの規模では許容）。
- Next.js のメジャー更新で API が変わりやすい（技術的負債 D8）→ 高度な機能を使わず、E2E テストで回帰を検知する。
- BFF に認可ロジックを書きたくなる誘惑がある → 認可は backend に一本化する（レビューで確認）。

## 検証方法（Compliance）

- `src/lib/bff/proxy.test.ts`: パス・クエリの転送、ヘッダーの許可リスト、複数 Set-Cookie、パストラバーサルの拒否、接続失敗時の 502。
- `ci-compose` ワークフロー: compose で起動した frontend の `/api/health` が backend の状態 `UP` を返すこと。

## 見直す条件（Revisit when）

- 公開範囲が社外にも広がり、CDN やエッジでのキャッシュが重要になったとき。
- BFF での集約処理が増え、backend との責務分担が曖昧になったとき。
