"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { apiFetch } from "@/lib/api/http";
import type { ConversationSummary } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";

export const conversationsKey = ["assistant-conversations"] as const;

/** 自分の会話の履歴（ADR-0014 の決定 3）。本人だけが見られ、最後に話した日から保持期間を過ぎると消える */
export function HistoryList() {
  const { data, error } = useQuery({
    queryKey: conversationsKey,
    queryFn: () => apiFetch<ConversationSummary[]>("/api/v1/assistant/conversations"),
  });
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-4 px-4 py-8">
      <h1 className="text-2xl font-bold">AI の回答の履歴</h1>
      <p className="text-sm text-muted-foreground">
        AI に質問した内容と回答は、あなただけが見られます。最後に質問した日から一定の期間（既定 90 日）がたつと自動で消えます。
      </p>
      {error ? (
        <p role="alert">{error.message}</p>
      ) : !data ? (
        <p role="status">読み込み中…</p>
      ) : data.length === 0 ? (
        <p>
          履歴はまだありません。<Link href="/assistant" className="underline">AI に質問する</Link>
        </p>
      ) : (
        <ul className="flex flex-col divide-y divide-border rounded-lg border border-border">
          {data.map((c) => (
            <li key={c.id} className="flex flex-wrap items-baseline justify-between gap-2 px-3 py-2">
              <Link href={`/assistant/history/${c.id}`} className="underline">
                {c.title}
              </Link>
              <span className="text-xs text-muted-foreground">{formatDateTime(c.lastMessageAt)}</span>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
