"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { ApiError } from "@/lib/api/http";
import { login, meQueryKey } from "@/lib/auth/api";
import { safeNextPath } from "@/lib/auth/session";

export function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setPending(true);
    setError(null);
    try {
      const me = await login(String(form.get("email") ?? ""), String(form.get("password") ?? ""));
      queryClient.setQueryData(meQueryKey, me);
      router.replace(safeNextPath(searchParams.get("next")));
    } catch (e) {
      setError(
        e instanceof ApiError && e.status === 401
          ? "メールアドレスまたはパスワードが正しくありません。"
          : e instanceof ApiError
            ? e.detail
            : "ログインできませんでした。時間をおいて再度お試しください。",
      );
      setPending(false);
    }
  }

  return (
    // method="post": 画面のスクリプトが読み込まれる前に送信されても、パスワードが URL に入らないようにする
    <form method="post" onSubmit={onSubmit} className="flex flex-col gap-4">
      <label className="flex flex-col gap-1 text-sm font-medium">
        メールアドレス
        <input
          name="email"
          type="email"
          autoComplete="username"
          required
          className="rounded-md border border-border bg-background px-3 py-2 text-base font-normal"
        />
      </label>
      <label className="flex flex-col gap-1 text-sm font-medium">
        パスワード
        <input
          name="password"
          type="password"
          autoComplete="current-password"
          required
          className="rounded-md border border-border bg-background px-3 py-2 text-base font-normal"
        />
      </label>
      {error && (
        <p role="alert" className="text-sm text-red-600 dark:text-red-400">
          {error}
        </p>
      )}
      <button
        type="submit"
        disabled={pending}
        className="rounded-md bg-foreground px-4 py-2 font-medium text-background disabled:opacity-60"
      >
        {pending ? "ログイン中…" : "ログイン"}
      </button>
    </form>
  );
}
