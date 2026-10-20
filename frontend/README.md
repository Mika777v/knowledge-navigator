# frontend（Next.js）

社内規程ナビの画面と BFF（Backend for Frontend）。判断の背景は [ADR-0005](../docs/adr/0005-nextjs-as-bff.md)。

| 項目 | 内容 |
|---|---|
| フレームワーク | Next.js 16（App Router、Cache Components 有効）/ React 19 / TypeScript |
| スタイル | Tailwind CSS 4、shadcn/ui の規約（`components.json`、`cn()`） |
| データ取得 | TanStack Query + openapi-fetch（型は backend の OpenAPI から生成） |
| テスト | Vitest + Testing Library |
| パッケージ管理 | pnpm（バージョンは `package.json` の `packageManager`） |

> この Next.js は学習データ時点から API や規約が変わっている。コードを書く前に `node_modules/next/dist/docs/` の該当ガイドを確認すること（`AGENTS.md`）。

## BFF の仕組み

```
ブラウザ ──/api/*──▶ Next.js（src/app/api/[...path]/route.ts）──▶ backend /api/*
```

- ブラウザは backend を直接呼ばない。同一オリジンになるため、Cookie（セッション）と CSRF トークンをそのまま扱える。
- 転送先は実行時の環境変数 `BACKEND_URL`（既定 `http://localhost:8080`）。ビルド済みイメージを環境ごとに使い回せる。
- 転送するリクエストヘッダーは許可リスト方式。`/api/../` のようなパスは拒否する。backend に接続できないときは内部情報を含めずに 502 を返す（`src/lib/bff/proxy.ts`）。
- `/api/health` は frontend のヘルスチェック（backend の状態も含める）。

## 開発

```bash
pnpm install
pnpm dev          # http://localhost:3000（backend は make up か ./gradlew bootRun で起動しておく）
pnpm lint
pnpm typecheck
pnpm test
pnpm build

# backend の OpenAPI から API の型を再生成（backend を起動した状態で）
pnpm api:types
```

## shadcn/ui のコンポーネント追加

```bash
pnpm dlx shadcn@latest add button input
```

`components.json` と `src/lib/utils.ts`（`cn`）は設定済み。コンポーネントは `src/components/ui/` に追加される。
