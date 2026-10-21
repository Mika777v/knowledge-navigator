"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { ApiError, apiFetch } from "@/lib/api/http";
import type { AdminFaq } from "@/lib/api/types";
import { STATUS_LABELS, VISIBILITY_LABELS } from "@/lib/content-labels";

export const adminFaqsKey = ["admin", "faqs"] as const;

/** 自部署が所管する FAQ の一覧（下書き・公開中・公開終了のすべて） */
export function FaqList() {
  const { data, error } = useQuery({
    queryKey: adminFaqsKey,
    queryFn: () => apiFetch<AdminFaq[]>("/api/v1/admin/faqs"),
  });

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-2xl font-bold">FAQ</h1>
        <Link href="/admin/faqs/new" className="rounded-md bg-foreground px-4 py-2 text-sm font-medium text-background">
          新しい FAQ
        </Link>
      </div>
      {error ? (
        <p role="alert">{error instanceof ApiError ? error.detail : "表示できませんでした。"}</p>
      ) : !data ? (
        <p className="text-muted-foreground">読み込み中…</p>
      ) : data.length === 0 ? (
        <p>所管する FAQ はまだありません。</p>
      ) : (
        <table className="w-full border-collapse text-left text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="py-1 pr-3">コード</th>
              <th className="py-1 pr-3">質問</th>
              <th className="py-1 pr-3">状態</th>
              <th className="py-1">公開範囲</th>
            </tr>
          </thead>
          <tbody>
            {data.map((f) => (
              <tr key={f.id} className="border-b border-border">
                <td className="py-1 pr-3 font-mono text-xs">{f.code}</td>
                <td className="py-1 pr-3">
                  <Link href={`/admin/faqs/${f.id}`} className="underline">
                    {f.question}
                  </Link>
                </td>
                <td className="py-1 pr-3">{STATUS_LABELS[f.status]}</td>
                <td className="py-1">{VISIBILITY_LABELS[f.visibility]}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
