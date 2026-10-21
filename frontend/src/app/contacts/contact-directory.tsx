"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { ContactCard } from "@/components/contact-card";
import { ApiError, apiFetch } from "@/lib/api/http";

/**
 * GET /api/v1/contacts の応答。OpenAPI では窓口の型名（ContactResponse）が別の API と重なっているため、
 * 生成した型ではなくここで定義する（生成した型と同じく、未設定の項目は null になる）
 */
type DirectoryContact = {
  code: string;
  name: string;
  description: string;
  email: string;
  phoneExt: string;
  chatUrl: string;
  officeHours: string;
  primary: boolean;
};
type DirectoryDepartment = {
  code: string;
  name: string;
  description: string;
  parentCode: string;
  contacts: DirectoryContact[];
};

/** 階層ごとの字下げ（部 → 課 → 係 まで） */
const INDENT = ["", "ml-4", "ml-8"];

/** 上位の部署をたどった階層の深さ */
function depthOf(dept: DirectoryDepartment, byCode: Map<string, DirectoryDepartment>): number {
  let depth = 0;
  for (let p: string | undefined = dept.parentCode; p && depth < INDENT.length - 1; p = byCode.get(p)?.parentCode)
    depth++;
  return depth;
}

function matches(text: string, ...values: (string | null | undefined)[]): boolean {
  return values.some((v) => v?.toLowerCase().includes(text));
}

/** 部署ごとの問い合わせ窓口の一覧。部署名・窓口名・説明で絞り込める */
export function ContactDirectory() {
  const [query, setQuery] = useState("");
  const { data, error } = useQuery({
    queryKey: ["contacts"],
    queryFn: () => apiFetch<DirectoryDepartment[]>("/api/v1/contacts"),
  });
  const text = query.trim().toLowerCase();
  const byCode = new Map(data?.map((d) => [d.code, d]));
  const departments = (data ?? [])
    .map((d) => ({
      ...d,
      // 部署名が合えばその部署の窓口をすべて、合わなければ合う窓口だけを出す
      contacts:
        !text || matches(text, d.name, d.description)
          ? d.contacts
          : d.contacts.filter((c) => matches(text, c.name, c.description)),
    }))
    .filter((d) => d.contacts.length > 0);

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-8">
      <header className="flex flex-col gap-2">
        <h1 className="text-2xl font-bold">問い合わせ先の一覧</h1>
        <p className="text-sm text-muted-foreground">
          どこに聞けばよいか分からないときは、各部署の「代表窓口」に問い合わせてください。
        </p>
      </header>
      <div className="flex flex-col gap-1 text-sm">
        <label htmlFor="contact-filter" className="font-medium">
          部署・窓口で絞り込む
        </label>
        <input
          id="contact-filter"
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="例: 人事、PC、経費"
          className="rounded-md border border-border bg-background px-3 py-2"
        />
      </div>
      {error && <p role="alert">{error instanceof ApiError ? error.detail : "読み込めませんでした。"}</p>}
      {!data && !error && <p className="text-muted-foreground">読み込み中…</p>}
      {data && departments.length === 0 && <p>「{query.trim()}」に合う部署・窓口はありません。</p>}
      {departments.map((d) => (
        <section
          key={d.code}
          aria-labelledby={`dept-${d.code}`}
          className={`flex flex-col gap-2 ${INDENT[depthOf(d, byCode)]}`}
        >
          <h2 id={`dept-${d.code}`} className="text-lg font-semibold">
            {d.name}
          </h2>
          {d.description && <p className="text-sm text-muted-foreground">{d.description}</p>}
          <div className="grid gap-2 sm:grid-cols-2">
            {d.contacts.map((c) => (
              <ContactCard key={c.code} contact={c} note={c.primary ? "代表窓口" : undefined} />
            ))}
          </div>
        </section>
      ))}
    </main>
  );
}
