"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { ApiError, apiFetch } from "@/lib/api/http";
import { fetchMe, meQueryKey, ROLE_LABELS } from "@/lib/auth/api";

/** backend と同じ最小文字数（OrganizationAdminService.MIN_PASSWORD_LENGTH） */
const MIN_PASSWORD_LENGTH = 8;
const inputClass = "rounded-md border border-border bg-background px-3 py-2";

/** 自分のアカウント。所属・ロールの確認と、パスワードの変更 */
export function AccountView() {
  const { data: me } = useQuery({ queryKey: meQueryKey, queryFn: fetchMe });
  return (
    <main className="mx-auto flex w-full max-w-xl flex-col gap-8 px-4 py-8">
      <h1 className="text-2xl font-bold">アカウント</h1>
      {me && (
        <dl className="grid grid-cols-[8rem_1fr] gap-y-2 text-sm">
          <dt className="text-muted-foreground">名前</dt>
          <dd>{me.displayName}</dd>
          <dt className="text-muted-foreground">メールアドレス</dt>
          <dd>{me.email}</dd>
          <dt className="text-muted-foreground">所属</dt>
          <dd>{me.department?.name ?? "所属なし"}</dd>
          <dt className="text-muted-foreground">ロール</dt>
          <dd>{ROLE_LABELS[me.role]}</dd>
        </dl>
      )}
      <p className="text-xs text-muted-foreground">所属やロールの変更は、システム管理者に依頼してください。</p>
      <PasswordForm email={me?.email ?? ""} />
    </main>
  );
}

function PasswordForm({ email }: { email: string }) {
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const currentPassword = String(form.get("currentPassword") ?? "");
    const newPassword = String(form.get("newPassword") ?? "");
    setDone(false);
    // 送る前に分かる誤りは、その場で知らせる
    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      setError(`新しいパスワードは${MIN_PASSWORD_LENGTH}文字以上にしてください。`);
      return;
    }
    if (newPassword !== form.get("confirmPassword")) {
      setError("新しいパスワード（確認）が一致しません。");
      return;
    }
    setPending(true);
    setError(null);
    try {
      await apiFetch<void>("/api/v1/me/password", { method: "POST", json: { currentPassword, newPassword } });
      formElement.reset();
      setDone(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.detail : "変更できませんでした。時間をおいて再度お試しください。");
    } finally {
      setPending(false);
    }
  }

  return (
    // method="post": 画面のスクリプトが読み込まれる前に送信されても、パスワードが URL に入らないようにする
    <form method="post" onSubmit={onSubmit} className="flex flex-col gap-4 text-sm">
      <h2 className="text-lg font-semibold">パスワードの変更</h2>
      {/* パスワード管理ツールが、どのアカウントのパスワードかを判断できるようにする */}
      <input type="hidden" name="username" autoComplete="username" value={email} />
      <div className="flex flex-col gap-1">
        <label htmlFor="current-password" className="font-medium">
          今のパスワード
        </label>
        <input
          id="current-password"
          name="currentPassword"
          type="password"
          autoComplete="current-password"
          required
          className={inputClass}
        />
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor="new-password" className="font-medium">
          新しいパスワード（{MIN_PASSWORD_LENGTH}文字以上）
        </label>
        <input
          id="new-password"
          name="newPassword"
          type="password"
          autoComplete="new-password"
          required
          className={inputClass}
        />
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor="confirm-password" className="font-medium">
          新しいパスワード（確認）
        </label>
        <input
          id="confirm-password"
          name="confirmPassword"
          type="password"
          autoComplete="new-password"
          required
          className={inputClass}
        />
      </div>
      {error && (
        <p role="alert" className="text-red-600 dark:text-red-400">
          {error}
        </p>
      )}
      {done && <p role="status">パスワードを変更しました。次回から新しいパスワードでログインしてください。</p>}
      <button
        type="submit"
        disabled={pending}
        className="self-start rounded-md bg-foreground px-4 py-2 font-medium text-background disabled:opacity-60"
      >
        変更する
      </button>
    </form>
  );
}
