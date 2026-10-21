"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

/** 検索の入力欄（backend の上限と同じ 200 文字まで） */
export const MAX_QUERY_LENGTH = 200;

export function SearchForm({ initialQuery = "", autoFocus = false }: { initialQuery?: string; autoFocus?: boolean }) {
  const router = useRouter();
  const [q, setQ] = useState(initialQuery);

  function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = q.trim();
    if (!trimmed) return;
    router.push(`/search?q=${encodeURIComponent(trimmed)}`);
  }

  return (
    <form role="search" onSubmit={onSubmit} className="flex gap-2">
      <label htmlFor="search-q" className="sr-only">
        知りたいこと
      </label>
      <input
        id="search-q"
        name="q"
        type="search"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        maxLength={MAX_QUERY_LENGTH}
        autoFocus={autoFocus}
        placeholder="例: 結婚したら何か制度ある？"
        className="min-w-0 flex-1 rounded-md border border-border bg-background px-3 py-2 text-base"
      />
      <button
        type="submit"
        disabled={!q.trim()}
        className="rounded-md bg-foreground px-4 py-2 font-medium text-background disabled:opacity-60"
      >
        検索
      </button>
    </form>
  );
}
