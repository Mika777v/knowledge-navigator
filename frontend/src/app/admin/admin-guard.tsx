"use client";

import { useQuery } from "@tanstack/react-query";
import { canUseAdmin, fetchMe, meQueryKey } from "@/lib/auth/api";

/**
 * 管理画面は、コンテンツ担当者とシステム管理者だけに表示する。
 * 画面を隠すのは使いやすさのためで、権限の確認は各 API が行う（ここを通っても API が 403 を返す）。
 */
export function AdminGuard({ children }: { children: React.ReactNode }) {
  const { data: me, isPending } = useQuery({ queryKey: meQueryKey, queryFn: fetchMe });
  if (isPending) return <p className="text-muted-foreground">読み込み中…</p>;
  if (!canUseAdmin(me)) {
    return <p role="alert">管理画面は、コンテンツ担当者とシステム管理者だけが利用できます。</p>;
  }
  return <>{children}</>;
}
