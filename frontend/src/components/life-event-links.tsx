"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { apiFetch } from "@/lib/api/http";
import type { LifeEventSummary } from "@/lib/api/types";

/** 「きっかけから探す」。きっかけの名前で検索すると、辞書でそのきっかけの制度に案内される */
export function LifeEventLinks() {
  const { data, isError } = useQuery({
    queryKey: ["life-events"],
    queryFn: () => apiFetch<LifeEventSummary[]>("/api/v1/life-events"),
  });
  if (isError) return null;
  if (!data) return <p className="text-sm text-muted-foreground">読み込み中…</p>;
  return (
    <ul className="grid grid-cols-2 gap-2 sm:grid-cols-4">
      {data.map((e) => (
        <li key={e.code}>
          <Link
            href={`/search?q=${encodeURIComponent(e.name)}`}
            title={e.description ?? undefined}
            className="block rounded-lg border border-border px-3 py-3 text-center font-medium hover:bg-foreground/5"
          >
            {e.name}
          </Link>
        </li>
      ))}
    </ul>
  );
}
