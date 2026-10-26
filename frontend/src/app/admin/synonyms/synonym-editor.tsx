"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { apiFetch } from "@/lib/api/http";
import type { SynonymGroup } from "@/lib/api/types";
import { ifMatch } from "@/lib/content-labels";
import { adminErrorMessage } from "../admin-errors";

const BASE = "/api/v1/admin/synonyms";
const synonymsKey = ["admin", "synonyms"] as const;

/** 「育休、育児休暇」のように区切った入力を語の配列にする（読点・カンマ・改行で区切る） */
export function splitTerms(input: string): string[] {
  return input
    .split(/[、,，\n]/)
    .map((t) => t.trim())
    .filter((t) => t.length > 0);
}

/**
 * 類義語辞書の確認と編集。グループの語はどれも同じ意味として検索を広げる（代表の語もグループの語に含まれる）。
 * 1つの語は1つのグループにだけ登録できる（重なると 409 の理由を出す）。
 */
export function SynonymEditor() {
  // 検索の分析（何も見つからなかった質問）から ?term= 付きで開かれたときは、その言葉を「同じ意味の言葉」に入れる
  const term = useSearchParams().get("term")?.trim() ?? "";
  const [input, setInput] = useState("");
  const [q, setQ] = useState("");
  const groups = useQuery({
    queryKey: [...synonymsKey, q],
    queryFn: () => apiFetch<SynonymGroup[]>(q ? `${BASE}?q=${encodeURIComponent(q)}` : BASE),
  });

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold">類義語辞書</h1>
        <p className="text-sm text-muted-foreground">
          同じ意味の言い方をまとめます。質問にグループの言葉が含まれると、ほかの言い方でも探します（例: 「育休」で「育児休業」の FAQ・条文も見つかる）。全角・半角や大文字・小文字は自動でそろえます。
        </p>
        <p className="text-sm text-muted-foreground">
          「入籍 → 結婚」のように、言葉から制度・きっかけへ直接案内したいときは
          <Link href="/admin/dictionary" className="underline">
            キーワード辞書
          </Link>
          を使います。
        </p>
      </header>

      <section aria-labelledby="new-heading" className="flex flex-col gap-3">
        <h2 id="new-heading" className="text-lg font-semibold">
          グループを追加
        </h2>
        {term && (
          <p className="text-sm">
            「{term}」を同じ意味の言葉に入れました。意味が同じになる代表の言葉（規程・制度で使っている言い方）を入力してください。
          </p>
        )}
        <GroupForm submitLabel="追加" initialTerms={term} />
      </section>

      <section aria-labelledby="list-heading" className="flex flex-col gap-3">
        <h2 id="list-heading" className="text-lg font-semibold">
          登録済みのグループ
        </h2>
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            setQ(input.trim());
          }}
        >
          <label htmlFor="synonym-q" className="sr-only">
            言葉で絞り込む
          </label>
          <input
            id="synonym-q"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="例: 育休"
            maxLength={100}
            className="min-w-0 flex-1 rounded-md border border-border bg-background px-3 py-2"
          />
          <button type="submit" className="rounded-md border border-border px-4 py-2">
            絞り込む
          </button>
        </form>
        {groups.error ? (
          <p role="alert">{adminErrorMessage(groups.error)}</p>
        ) : !groups.data ? (
          <p className="text-sm text-muted-foreground">読み込み中…</p>
        ) : groups.data.length === 0 ? (
          <p className="text-sm">{q ? `「${q}」を含むグループはありません。` : "まだグループがありません。"}</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {groups.data.map((g) => (
              <GroupRow key={g.id} group={g} />
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function GroupRow({ group }: { group: SynonymGroup }) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const remove = useMutation({
    mutationFn: () => apiFetch<void>(`${BASE}/${encodeURIComponent(group.id)}`, { method: "DELETE" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: synonymsKey }),
  });

  return (
    <li className="flex flex-col gap-2 rounded-md border border-border p-3 text-sm">
      {editing ? (
        <GroupForm group={group} submitLabel="保存" onDone={() => setEditing(false)} />
      ) : (
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-medium">{group.canonical}</span>
          <ul aria-label={`「${group.canonical}」と同じ意味の言葉`} className="flex flex-wrap gap-1">
            {group.terms
              .filter((t) => t !== group.canonical)
              .map((t) => (
                <li key={t} className="rounded bg-foreground/10 px-1.5 py-0.5 text-xs">
                  {t}
                </li>
              ))}
          </ul>
          <span className="ml-auto flex gap-2">
            <button type="button" onClick={() => setEditing(true)} className="rounded-md border border-border px-2 py-0.5">
              編集
            </button>
            <button
              type="button"
              disabled={remove.isPending}
              onClick={() => {
                if (window.confirm(`「${group.canonical}」のグループを削除しますか？`)) remove.mutate();
              }}
              className="rounded-md border border-border px-2 py-0.5"
            >
              削除
            </button>
          </span>
        </div>
      )}
      {remove.error && <p role="alert">{adminErrorMessage(remove.error)}</p>}
    </li>
  );
}

/** グループの追加（group なし）と編集（group あり。If-Match で保存） */
function GroupForm({
  group,
  submitLabel,
  onDone,
  initialTerms = "",
}: {
  group?: SynonymGroup;
  submitLabel: string;
  onDone?: () => void;
  initialTerms?: string;
}) {
  const queryClient = useQueryClient();
  const [canonical, setCanonical] = useState(group?.canonical ?? "");
  const [terms, setTerms] = useState(group ? group.terms.filter((t) => t !== group.canonical).join("、") : initialTerms);
  const save = useMutation({
    mutationFn: () => {
      const json = { canonical: canonical.trim(), terms: splitTerms(terms) };
      return group
        ? apiFetch<SynonymGroup>(`${BASE}/${encodeURIComponent(group.id)}`, {
            method: "PUT",
            json,
            headers: ifMatch(group.rowVersion),
          })
        : apiFetch<SynonymGroup>(BASE, { method: "POST", json });
    },
    onSuccess: () => {
      if (!group) {
        setCanonical("");
        setTerms("");
      }
      onDone?.();
      return queryClient.invalidateQueries({ queryKey: synonymsKey });
    },
  });
  const idPrefix = group ? `synonym-${group.id}` : "synonym-new";

  return (
    <form
      className="flex flex-col gap-2 text-sm"
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate();
      }}
    >
      <div className="flex flex-wrap items-end gap-2">
        <div className="flex w-48 flex-col gap-1">
          <label htmlFor={`${idPrefix}-canonical`}>代表の言葉</label>
          <input
            id={`${idPrefix}-canonical`}
            value={canonical}
            onChange={(e) => setCanonical(e.target.value)}
            maxLength={100}
            placeholder="例: 育児休業"
            className="rounded-md border border-border bg-background px-3 py-2"
          />
        </div>
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <label htmlFor={`${idPrefix}-terms`}>同じ意味の言葉（「、」で区切る）</label>
          <input
            id={`${idPrefix}-terms`}
            value={terms}
            onChange={(e) => setTerms(e.target.value)}
            placeholder="例: 育休、育児休暇"
            className="rounded-md border border-border bg-background px-3 py-2"
          />
        </div>
        <button
          type="submit"
          disabled={!canonical.trim() || splitTerms(terms).length === 0 || save.isPending}
          className="rounded-md bg-foreground px-4 py-2 font-medium text-background disabled:opacity-60"
        >
          {submitLabel}
        </button>
        {onDone && (
          <button type="button" onClick={onDone} className="rounded-md border border-border px-4 py-2">
            やめる
          </button>
        )}
      </div>
      <p className="text-xs text-muted-foreground">
        意味の広すぎる言葉（「車」「休み」など）は、関係のない FAQ・条文まで拾うため登録しないでください。
      </p>
      {save.error && <p role="alert">{adminErrorMessage(save.error)}</p>}
    </form>
  );
}
