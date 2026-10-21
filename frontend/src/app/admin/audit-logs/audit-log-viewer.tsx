"use client";

import { useInfiniteQuery } from "@tanstack/react-query";
import { useState } from "react";
import { ApiError, apiFetch } from "@/lib/api/http";
import type { AuditLogPage } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";

const RESOURCE_TYPES: Record<string, string> = {
  DOCUMENT: "文書",
  PROGRAM: "制度",
  FAQ: "FAQ",
  NAVIGATION_KEYWORD: "キーワード",
};

/** 操作の表示名（backend が記録する操作コード）。知らないコードはそのまま表示する */
export const ACTION_LABELS: Record<string, string> = {
  DOCUMENT_CREATED: "文書を作成",
  DOCUMENT_UPDATED: "文書を更新",
  DOCUMENT_VERSION_CREATED: "文書の版を作成",
  DOCUMENT_PUBLISHED: "文書を公開",
  DOCUMENT_ARCHIVED: "文書を廃止",
  PROGRAM_CREATED: "制度を作成",
  PROGRAM_UPDATED: "制度を更新",
  PROGRAM_BASIS_REPLACED: "制度の根拠を変更",
  PROGRAM_CONTACTS_REPLACED: "制度の窓口を変更",
  PROGRAM_PUBLISHED: "制度を公開",
  PROGRAM_ARCHIVED: "制度を廃止",
  FAQ_CREATED: "FAQ を作成",
  FAQ_UPDATED: "FAQ を更新",
  FAQ_PUBLISHED: "FAQ を公開",
  FAQ_ARCHIVED: "FAQ を廃止",
  NAVIGATION_KEYWORD_ADDED: "キーワードを追加",
  NAVIGATION_KEYWORD_DELETED: "キーワードを削除",
};

type Filter = { from: string; to: string; action: string; resourceType: string };
const EMPTY: Filter = { from: "", to: "", action: "", resourceType: "" };

/** 監査ログ（システム管理者だけ）。新しい順に表示し、「さらに読み込む」で続きを取得する */
export function AuditLogViewer() {
  const [draft, setDraft] = useState<Filter>(EMPTY);
  const [filter, setFilter] = useState<Filter>(EMPTY);

  const logs = useInfiniteQuery({
    queryKey: ["admin", "audit-logs", filter],
    initialPageParam: null as number | null,
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ size: "50" });
      for (const [key, value] of Object.entries(filter)) {
        if (value.trim()) params.set(key, value.trim());
      }
      if (pageParam !== null) params.set("cursor", String(pageParam));
      return apiFetch<AuditLogPage>(`/api/v1/admin/audit-logs?${params}`);
    },
    getNextPageParam: (last) => last.nextCursor ?? null,
  });
  const items = logs.data?.pages.flatMap((p) => p.items) ?? [];

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-bold">監査ログ</h1>
      <form
        className="flex flex-wrap items-end gap-2 text-sm"
        onSubmit={(e) => {
          e.preventDefault();
          setFilter(draft);
        }}
      >
        <label className="flex flex-col gap-1">
          開始日
          <input
            type="date"
            value={draft.from}
            onChange={(e) => setDraft({ ...draft, from: e.target.value })}
            className="rounded-md border border-border bg-background px-2 py-1"
          />
        </label>
        <label className="flex flex-col gap-1">
          終了日
          <input
            type="date"
            value={draft.to}
            onChange={(e) => setDraft({ ...draft, to: e.target.value })}
            className="rounded-md border border-border bg-background px-2 py-1"
          />
        </label>
        <label className="flex flex-col gap-1">
          対象
          <select
            value={draft.resourceType}
            onChange={(e) => setDraft({ ...draft, resourceType: e.target.value })}
            className="rounded-md border border-border bg-background px-2 py-1"
          >
            <option value="">すべて</option>
            {Object.entries(RESOURCE_TYPES).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1">
          操作
          <input
            value={draft.action}
            onChange={(e) => setDraft({ ...draft, action: e.target.value })}
            placeholder="例: DOCUMENT_PUBLISHED"
            className="rounded-md border border-border bg-background px-2 py-1"
          />
        </label>
        <button type="submit" className="rounded-md border border-border px-3 py-1">
          絞り込む
        </button>
      </form>

      {logs.error ? (
        <p role="alert" className="text-sm">
          {logs.error instanceof ApiError ? logs.error.detail : "表示できませんでした。時間をおいて再度お試しください。"}
        </p>
      ) : logs.isPending ? (
        <p className="text-sm text-muted-foreground">読み込み中…</p>
      ) : items.length === 0 ? (
        <p className="text-sm">該当する記録はありません。</p>
      ) : (
        <table className="w-full border-collapse text-left text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="py-1 pr-3">日時</th>
              <th className="py-1 pr-3">操作者</th>
              <th className="py-1 pr-3">操作</th>
              <th className="py-1 pr-3">対象</th>
              <th className="py-1">内容</th>
            </tr>
          </thead>
          <tbody>
            {items.map((e) => (
              <tr key={e.id} className="border-b border-border align-top">
                <td className="py-1 pr-3 whitespace-nowrap">{formatDateTime(e.occurredAt)}</td>
                <td className="py-1 pr-3">{e.actor?.displayName ?? "（システム）"}</td>
                <td className="py-1 pr-3">
                  {ACTION_LABELS[e.action] ?? e.action}
                  <div className="font-mono text-xs text-muted-foreground">{e.action}</div>
                </td>
                <td className="py-1 pr-3">
                  {RESOURCE_TYPES[e.resourceType] ?? e.resourceType}
                  {e.resourceId && (
                    <span className="ml-1 font-mono text-xs text-muted-foreground" title={e.resourceId}>
                      {e.resourceId.slice(0, 8)}
                    </span>
                  )}
                </td>
                <td className="py-1">
                  {Object.entries(e.details ?? {}).map(([key, value]) => (
                    <div key={key}>
                      <span className="text-muted-foreground">{key}:</span>{" "}
                      {typeof value === "string" ? value : JSON.stringify(value)}
                    </div>
                  ))}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {logs.hasNextPage && (
        <button
          type="button"
          onClick={() => void logs.fetchNextPage()}
          disabled={logs.isFetchingNextPage}
          className="self-start rounded-md border border-border px-3 py-1 text-sm"
        >
          {logs.isFetchingNextPage ? "読み込み中…" : "さらに読み込む"}
        </button>
      )}
    </div>
  );
}
