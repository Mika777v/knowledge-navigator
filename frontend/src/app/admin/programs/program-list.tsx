"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { apiFetch } from "@/lib/api/http";
import { STATUS_LABELS, VISIBILITY_LABELS } from "@/lib/content-labels";
import { adminErrorMessage } from "../admin-errors";
import { type AdminProgram, adminProgramsKey } from "./program-api";

/** 自部署が所管する制度の一覧（下書き・公開中・公開終了のすべて） */
export function ProgramList() {
  const { data, error } = useQuery({
    queryKey: adminProgramsKey,
    queryFn: () => apiFetch<AdminProgram[]>("/api/v1/admin/programs"),
  });

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-2xl font-bold">制度</h1>
        <Link href="/admin/programs/new" className="rounded-md bg-foreground px-4 py-2 text-sm font-medium text-background">
          新しい制度
        </Link>
      </div>
      {error ? (
        <p role="alert">{adminErrorMessage(error)}</p>
      ) : !data ? (
        <p className="text-muted-foreground">読み込み中…</p>
      ) : data.length === 0 ? (
        <p>所管する制度はまだありません。</p>
      ) : (
        <table className="w-full border-collapse text-left text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="py-1 pr-3">コード</th>
              <th className="py-1 pr-3">制度名</th>
              <th className="py-1 pr-3">状態</th>
              <th className="py-1 pr-3">公開範囲</th>
              <th className="py-1 text-right">根拠・窓口</th>
            </tr>
          </thead>
          <tbody>
            {data.map((p) => (
              <tr key={p.id} className="border-b border-border">
                <td className="py-1 pr-3 font-mono text-xs">{p.code}</td>
                <td className="py-1 pr-3">
                  <Link href={`/admin/programs/${p.id}`} className="underline">
                    {p.name}
                  </Link>
                </td>
                <td className="py-1 pr-3">{STATUS_LABELS[p.status]}</td>
                <td className="py-1 pr-3">{VISIBILITY_LABELS[p.visibility]}</td>
                <td className="py-1 text-right">
                  {p.basis.length}・{p.contacts.length}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
