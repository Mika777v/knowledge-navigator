"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { apiFetch } from "@/lib/api/http";
import { STATUS_LABELS, VISIBILITY_LABELS } from "@/lib/content-labels";
import { type AdminDocument, adminDocumentsKey, DOCUMENT_TYPE_LABELS, errorMessage } from "./document-api";

/** 自部署が所管する規程・文書の一覧 */
export function DocumentList() {
  const { data, error } = useQuery({
    queryKey: adminDocumentsKey,
    queryFn: () => apiFetch<AdminDocument[]>("/api/v1/admin/documents"),
  });

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-2xl font-bold">規程</h1>
        <Link
          href="/admin/documents/new"
          className="rounded-md bg-foreground px-4 py-2 text-sm font-medium text-background"
        >
          新しい文書
        </Link>
      </div>
      {error ? (
        <p role="alert">{errorMessage(error)}</p>
      ) : !data ? (
        <p className="text-muted-foreground">読み込み中…</p>
      ) : data.length === 0 ? (
        <p>所管する文書はまだありません。</p>
      ) : (
        <table className="w-full border-collapse text-left text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="py-1 pr-3">コード</th>
              <th className="py-1 pr-3">文書名</th>
              <th className="py-1 pr-3">種類</th>
              <th className="py-1 pr-3">状態</th>
              <th className="py-1">公開範囲</th>
            </tr>
          </thead>
          <tbody>
            {data.map((d) => (
              <tr key={d.id} className="border-b border-border">
                <td className="py-1 pr-3 font-mono text-xs">{d.code}</td>
                <td className="py-1 pr-3">
                  <Link href={`/admin/documents/${d.id}`} className="underline">
                    {d.title}
                  </Link>
                </td>
                <td className="py-1 pr-3">{DOCUMENT_TYPE_LABELS[d.documentType]}</td>
                <td className="py-1 pr-3">{STATUS_LABELS[d.status]}</td>
                <td className="py-1">{VISIBILITY_LABELS[d.visibility]}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
