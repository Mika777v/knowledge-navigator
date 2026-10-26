"use client";

import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect, useId, useState } from "react";
import { apiFetch } from "@/lib/api/http";
import type { SuggestionItem } from "@/lib/api/types";

/** 検索の入力欄（backend の上限と同じ 200 文字まで） */
export const MAX_QUERY_LENGTH = 200;
/** 入力補完の候補を探す入力の長さの上限（backend と同じ） */
const MAX_SUGGEST_LENGTH = 50;
/** 打ち終わるまで待つ時間（1文字ごとに API を呼ばないように） */
const SUGGEST_DELAY_MS = 200;

const TYPE_LABELS: Record<string, string> = { LIFE_EVENT: "きっかけ", PROGRAM: "制度" };

/**
 * 検索の入力欄と入力補完（E12-T6）。入力に一致するきっかけ・制度名を候補に出す。
 * 候補は ↑↓ で選び Enter で開く（制度は詳細へ、きっかけはその名前で検索）。Esc で閉じる。
 */
export function SearchForm({ initialQuery = "", autoFocus = false }: { initialQuery?: string; autoFocus?: boolean }) {
  const router = useRouter();
  const listId = useId();
  const [q, setQ] = useState(initialQuery);
  const [typed, setTyped] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);

  // 打ち終わってから候補を探す
  useEffect(() => {
    const timer = setTimeout(() => setTyped(q.trim()), SUGGEST_DELAY_MS);
    return () => clearTimeout(timer);
  }, [q]);
  const { data } = useQuery({
    queryKey: ["suggest", typed],
    queryFn: () => apiFetch<SuggestionItem[]>(`/api/v1/search/suggest?q=${encodeURIComponent(typed)}`),
    enabled: open && typed.length > 0 && typed.length <= MAX_SUGGEST_LENGTH,
    staleTime: 60_000,
  });
  const suggestions = open && typed.length > 0 ? (data ?? []) : [];
  const expanded = suggestions.length > 0;

  function search(text: string) {
    const trimmed = text.trim();
    if (!trimmed) return;
    setOpen(false);
    router.push(`/search?q=${encodeURIComponent(trimmed)}`);
  }

  function choose(s: SuggestionItem) {
    setOpen(false);
    if (s.type === "PROGRAM") {
      router.push(`/programs/${encodeURIComponent(s.id)}`);
    } else {
      setQ(s.label);
      search(s.label);
    }
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    // 変換中（IME）の Enter・矢印は候補の操作に使わない
    if (event.nativeEvent.isComposing) return;
    if (event.key === "Escape") {
      setOpen(false);
      setActive(-1);
    } else if (event.key === "ArrowDown" && expanded) {
      event.preventDefault();
      setActive((i) => (i + 1) % suggestions.length);
    } else if (event.key === "ArrowUp" && expanded) {
      event.preventDefault();
      setActive((i) => (i <= 0 ? suggestions.length - 1 : i - 1));
    } else if (event.key === "Enter" && expanded && active >= 0) {
      event.preventDefault();
      choose(suggestions[active]);
    }
  }

  return (
    <form
      role="search"
      onSubmit={(event) => {
        event.preventDefault();
        search(q);
      }}
      className="relative flex gap-2"
    >
      <label htmlFor="search-q" className="sr-only">
        知りたいこと
      </label>
      <input
        id="search-q"
        name="q"
        type="search"
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={expanded}
        aria-controls={listId}
        aria-activedescendant={expanded && active >= 0 ? `${listId}-${active}` : undefined}
        autoComplete="off"
        value={q}
        onChange={(e) => {
          setQ(e.target.value);
          setOpen(true);
          setActive(-1);
        }}
        onKeyDown={onKeyDown}
        // 候補のクリックより先に閉じないよう、少し待ってから閉じる
        onBlur={() => setTimeout(() => setOpen(false), 150)}
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
      <ul
        id={listId}
        role="listbox"
        aria-label="入力の候補"
        hidden={!expanded}
        className="absolute top-full left-0 z-10 mt-1 w-full overflow-hidden rounded-md border border-border bg-background shadow"
      >
        {suggestions.map((s, i) => (
          <li
            key={`${s.type}-${s.id}`}
            id={`${listId}-${i}`}
            role="option"
            aria-selected={i === active}
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => choose(s)}
            className={`flex cursor-pointer items-center gap-2 px-3 py-2 text-sm hover:bg-foreground/5 ${i === active ? "bg-foreground/10" : ""}`}
          >
            <span className="rounded bg-foreground/10 px-1.5 py-0.5 text-xs">{TYPE_LABELS[s.type] ?? s.type}</span>
            {s.label}
          </li>
        ))}
      </ul>
    </form>
  );
}
