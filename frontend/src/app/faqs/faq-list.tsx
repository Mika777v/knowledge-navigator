"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { QueryState } from "@/components/query-state";
import { apiFetch } from "@/lib/api/http";
import type { CategoryNode, FaqSummary } from "@/lib/api/types";

/**
 * よくある質問の一覧。カテゴリ（?category=。親カテゴリを選ぶと子カテゴリも含む）で絞り込み、
 * 質問の文で画面の中をさらに絞り込める。カテゴリごとに見出しを付けて並べる
 */
export function FaqList() {
  const category = useSearchParams().get("category");
  const [text, setText] = useState("");
  const categories = useQuery({
    queryKey: ["categories"],
    queryFn: () => apiFetch<CategoryNode[]>("/api/v1/categories"),
  });
  const faqs = useQuery({
    queryKey: ["faqs", category],
    queryFn: () => apiFetch<FaqSummary[]>(`/api/v1/faqs${category ? `?category=${encodeURIComponent(category)}` : ""}`),
  });
  const words = text.trim().toLowerCase().split(/\s+/).filter(Boolean);
  const shown = (faqs.data ?? []).filter((f) => words.every((w) => f.question.toLowerCase().includes(w)));
  const groups = new Map<string, FaqSummary[]>();
  for (const f of shown) groups.set(f.category.name, [...(groups.get(f.category.name) ?? []), f]);
  const selected = categories.data?.find((c) => c.slug === category)?.name;

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-8">
      <h1 className="text-2xl font-bold">よくある質問</h1>
      <nav aria-label="カテゴリで絞り込む" className="flex flex-wrap gap-1.5 text-sm">
        <Link
          href="/faqs"
          aria-current={!category ? "page" : undefined}
          className="rounded-full border border-border px-3 py-0.5 aria-[current=page]:bg-foreground aria-[current=page]:text-background"
        >
          すべて
        </Link>
        {categories.data?.map((c) => (
          <Link
            key={c.slug}
            href={`/faqs?category=${encodeURIComponent(c.slug)}`}
            aria-current={category === c.slug ? "page" : undefined}
            className="rounded-full border border-border px-3 py-0.5 aria-[current=page]:bg-foreground aria-[current=page]:text-background"
          >
            {c.name}
          </Link>
        ))}
      </nav>
      <div className="flex flex-col gap-1 text-sm">
        <label htmlFor="faq-filter" className="font-medium">
          質問の文で絞り込む
        </label>
        <input
          id="faq-filter"
          type="search"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="例: 結婚、在宅"
          className="rounded-md border border-border bg-background px-3 py-2"
        />
      </div>
      {!faqs.data ? (
        <QueryState isPending={faqs.isPending} error={faqs.error} />
      ) : (
        <>
          <p role="status" className="text-sm text-muted-foreground">
            {selected ? `「${selected}」の` : ""}質問 {shown.length} 件
          </p>
          {shown.length === 0 && (
            <p>
              該当する質問はありません。言い方を変えるか、
              <Link href="/" className="underline">
                ホームの検索
              </Link>
              を使ってください。
            </p>
          )}
          {[...groups].map(([name, list]) => (
            <section key={name} aria-label={name} className="flex flex-col gap-2">
              <h2 className="font-semibold">{name}</h2>
              <ul className="flex list-disc flex-col gap-1 pl-5">
                {list.map((f) => (
                  <li key={f.id}>
                    <Link href={`/faqs/${f.id}`} className="underline">
                      {f.question}
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </>
      )}
    </main>
  );
}
