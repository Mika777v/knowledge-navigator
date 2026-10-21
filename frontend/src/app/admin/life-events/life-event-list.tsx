"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { apiFetch } from "@/lib/api/http";
import { type AdminLifeEvent, adminLifeEventsKey, errorMessage } from "./life-event-api";

/** きっかけの一覧（ホーム画面の「きっかけから探す」に表示順で並ぶ） */
export function LifeEventList() {
  const { data, error } = useQuery({
    queryKey: adminLifeEventsKey,
    queryFn: () => apiFetch<AdminLifeEvent[]>("/api/v1/admin/life-events"),
  });

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-2xl font-bold">きっかけ</h1>
        <Link
          href="/admin/life-events/new"
          className="rounded-md bg-foreground px-4 py-2 text-sm font-medium text-background"
        >
          新しいきっかけ
        </Link>
      </div>
      <p className="text-sm text-muted-foreground">
        結婚・出産・引越しなど、社員の状況から使える制度を案内します。部署をまたぐため、どの部署のコンテンツ担当者でも編集できます。
      </p>
      {error ? (
        <p role="alert">{errorMessage(error)}</p>
      ) : !data ? (
        <p className="text-muted-foreground">読み込み中…</p>
      ) : (
        <table className="w-full border-collapse text-left text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="py-1 pr-3">表示順</th>
              <th className="py-1 pr-3">名前</th>
              <th className="py-1 pr-3">コード</th>
              <th className="py-1">まず確認する制度</th>
            </tr>
          </thead>
          <tbody>
            {data.map((e) => (
              <tr key={e.code} className="border-b border-border align-top">
                <td className="py-1 pr-3">{e.sortOrder}</td>
                <td className="py-1 pr-3">
                  <Link href={`/admin/life-events/${encodeURIComponent(e.code)}`} className="underline">
                    {e.name}
                  </Link>
                </td>
                <td className="py-1 pr-3 font-mono text-xs">{e.code}</td>
                <td className="py-1">
                  {e.programs
                    .filter((p) => p.relation === "PRIMARY")
                    .map((p) => p.name)
                    .join("、")}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
