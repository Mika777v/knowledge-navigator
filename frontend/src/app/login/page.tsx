import type { Metadata } from "next";
import { Suspense } from "react";
import { LoginForm } from "./login-form";

export const metadata: Metadata = { title: "ログイン | 社内規程ナビ" };

export default function LoginPage() {
  return (
    <main className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center gap-6 px-4 py-16">
      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold">社内規程ナビ</h1>
        <p className="text-sm text-muted-foreground">社員アカウントでログインしてください。</p>
      </header>
      {/* useSearchParams を使うため Suspense で囲む */}
      <Suspense>
        <LoginForm />
      </Suspense>
      <p className="text-xs text-muted-foreground">
        デモ用のユーザーとパスワードは data/README.md を参照してください。表示されるデータはすべて架空のものです。
      </p>
    </main>
  );
}
