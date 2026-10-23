"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { QueryState } from "@/components/query-state";
import { TagLinks } from "@/components/tag-links";
import { apiFetch } from "@/lib/api/http";
import type { DocumentSummary, TagCount } from "@/lib/api/types";

const TAG_TYPE_LABELS: Record<string, string> = { TARGET: "対象者", TOPIC: "話題", FREE: "その他" };

/** 閲覧できる規程の一覧。タグ（対象者・話題）で絞り込める（?tag=） */
export function DocumentList() {
  const tag = useSearchParams().get("tag");
  const tags = useQuery({ queryKey: ["tags"], queryFn: () => apiFetch<TagCount[]>("/api/v1/tags") });
  const documents = useQuery({
    queryKey: ["documents", tag],
    queryFn: () =>
      apiFetch<DocumentSummary[]>(`/api/v1/documents${tag ? `?tag=${encodeURIComponent(tag)}` : ""}`),
  });
  const groups = Object.entries(TAG_TYPE_LABELS)
    .map(([type, label]) => ({ label, tags: (tags.data ?? []).filter((t) => t.type === type) }))
    .filter((g) => g.tags.length > 0);

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-8">
      <h1 className="text-2xl font-bold">規程の一覧</h1>
      <nav aria-label="タグで絞り込む" className="flex flex-col gap-2 text-sm">
        {groups.map((g) => (
          <div key={g.label} className="flex flex-wrap items-center gap-1.5">
            <span className="w-14 text-xs text-muted-foreground">{g.label}</span>
            {g.tags.map((t) => (
              <Link
                key={t.name}
                href={tag === t.name ? "/documents" : `/documents?tag=${encodeURIComponent(t.name)}`}
                aria-current={tag === t.name ? "page" : undefined}
                className="rounded-full border border-border px-2.5 py-0.5 text-xs aria-[current=page]:bg-foreground aria-[current=page]:text-background"
              >
                {t.name}（{t.documentCount}）
              </Link>
            ))}
          </div>
        ))}
      </nav>
      {tag && (
        <p className="text-sm">
          「{tag}」のタグが付いた規程を表示しています。{" "}
          <Link href="/documents" className="underline">
            絞り込みをやめる
          </Link>
        </p>
      )}
      {!documents.data ? (
        <QueryState isPending={documents.isPending} error={documents.error} />
      ) : documents.data.length === 0 ? (
        <p>該当する規程はありません。</p>
      ) : (
        <ul className="flex flex-col gap-4">
          {documents.data.map((d) => (
            <li key={d.id} className="flex flex-col gap-1 border-b border-border pb-4">
              <Link href={`/documents/${d.id}`} className="font-semibold underline">
                {d.title}
              </Link>
              <p className="text-xs text-muted-foreground">
                {d.code}・所管: {d.ownerDepartment.name}・{d.versionLabel}（{d.effectiveFrom} 施行）
              </p>
              {d.summary && <p className="text-sm">{d.summary}</p>}
              <TagLinks tags={d.tags} label={`${d.title}のタグ`} />
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
