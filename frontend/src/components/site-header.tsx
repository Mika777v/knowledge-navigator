"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { canUseAdmin, fetchMe, logout, meQueryKey, ROLE_LABELS } from "@/lib/auth/api";

/** 全画面共通のヘッダー。ログイン中のユーザーとログアウトボタンを表示する（ログイン画面では出さない） */
export function SiteHeader() {
  const pathname = usePathname();
  if (pathname === "/login") return null;
  return <SignedInHeader />;
}

function SignedInHeader() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { data: me } = useQuery({ queryKey: meQueryKey, queryFn: fetchMe });

  async function onLogout() {
    try {
      await logout();
    } finally {
      queryClient.clear();
      router.replace("/login");
    }
  }

  return (
    <header className="border-b border-border">
      <div className="mx-auto flex w-full max-w-5xl items-center justify-between gap-4 px-4 py-3">
        <Link href="/" className="font-bold whitespace-nowrap">
          社内規程ナビ
        </Link>
        <div className="flex items-center gap-3 text-sm">
          {me && (
            <span className="truncate">
              {me.displayName}
              {/* 狭い画面では名前だけを出す */}
              <span className="ml-2 hidden text-muted-foreground sm:inline">
                {me.department?.name ?? "所属なし"}・{ROLE_LABELS[me.role]}
              </span>
            </span>
          )}
          <Link href="/account" className="whitespace-nowrap underline">
            アカウント
          </Link>
          {canUseAdmin(me) && (
            <Link href="/admin" className="whitespace-nowrap underline">
              管理
            </Link>
          )}
          <button
            type="button"
            onClick={onLogout}
            className="rounded-md border border-border px-3 py-1 whitespace-nowrap hover:bg-foreground/5"
          >
            ログアウト
          </button>
        </div>
      </div>
    </header>
  );
}
