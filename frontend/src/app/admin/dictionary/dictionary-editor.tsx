"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { ApiError, apiFetch } from "@/lib/api/http";
import type { KeywordResponse, KeywordTarget, KeywordTargetType } from "@/lib/api/types";

const BASE = "/api/v1/admin/navigation-keywords";

export const TARGET_TYPE_LABELS: Record<KeywordTargetType, string> = {
  LIFE_EVENT: "きっかけ",
  PROGRAM: "制度",
  DOCUMENT: "規程",
  FAQ: "FAQ",
  CONTACT: "窓口",
};

const keywordsKey = ["admin", "navigation-keywords"] as const;

function errorMessage(error: unknown) {
  return error instanceof ApiError ? error.detail : "処理できませんでした。時間をおいて再度お試しください。";
}

/**
 * ナビゲーションキーワード辞書の確認と編集。
 * 「言葉から確認」で利用者の言葉がどこに案内されるかを見て、「案内先から確認・追加」で言葉を足す・消す。
 */
export function DictionaryEditor() {
  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold">キーワード辞書</h1>
        <p className="text-sm text-muted-foreground">
          質問にここで登録した言葉が含まれると、その案内先（制度・きっかけなど）を検索結果の上位に出します。全角・半角や大文字・小文字は自動でそろえます。
        </p>
      </header>
      <TermLookup />
      <TargetKeywords />
    </div>
  );
}

function TermLookup() {
  // 分析画面の「辞書で確認」から ?term= 付きで開かれたときは、その言葉を確認した状態にする
  const initial = useSearchParams().get("term")?.trim() ?? "";
  const [input, setInput] = useState(initial);
  const [term, setTerm] = useState(initial);
  const { data, isFetching, error } = useQuery({
    queryKey: [...keywordsKey, "term", term],
    queryFn: () => apiFetch<KeywordResponse[]>(`${BASE}?term=${encodeURIComponent(term)}`),
    enabled: term.length > 0,
  });

  return (
    <section aria-labelledby="lookup-heading" className="flex flex-col gap-3">
      <h2 id="lookup-heading" className="text-lg font-semibold">
        言葉から確認
      </h2>
      <form
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          setTerm(input.trim());
        }}
      >
        <label htmlFor="lookup-term" className="sr-only">
          確認する言葉
        </label>
        <input
          id="lookup-term"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="例: 入籍"
          maxLength={100}
          className="min-w-0 flex-1 rounded-md border border-border bg-background px-3 py-2"
        />
        <button type="submit" disabled={!input.trim()} className="rounded-md border border-border px-4 py-2">
          確認
        </button>
      </form>
      {term && (
        <div className="flex flex-col gap-2 text-sm">
          {error ? (
            <p role="alert">{errorMessage(error)}</p>
          ) : isFetching && !data ? (
            <p className="text-muted-foreground">読み込み中…</p>
          ) : data && data.length > 0 ? (
            <KeywordTable keywords={data} showTarget />
          ) : (
            <p>「{term}」は辞書に登録されていません。</p>
          )}
          <Link href={`/search?q=${encodeURIComponent(term)}`} className="self-start underline">
            「{term}」で検索してみる
          </Link>
        </div>
      )}
    </section>
  );
}

function TargetKeywords() {
  const queryClient = useQueryClient();
  const [type, setType] = useState<KeywordTargetType>("LIFE_EVENT");
  const [targetId, setTargetId] = useState("");
  const [term, setTerm] = useState("");
  const [weight, setWeight] = useState("1.0");

  const targets = useQuery({
    queryKey: [...keywordsKey, "targets", type],
    queryFn: () => apiFetch<KeywordTarget[]>(`${BASE}/targets?targetType=${type}`),
  });
  const keywords = useQuery({
    queryKey: [...keywordsKey, "target", type, targetId],
    queryFn: () => apiFetch<KeywordResponse[]>(`${BASE}?targetType=${type}&targetId=${encodeURIComponent(targetId)}`),
    enabled: targetId !== "",
  });
  const add = useMutation({
    mutationFn: () =>
      apiFetch<KeywordResponse>(BASE, {
        method: "POST",
        json: { term: term.trim(), targetType: type, targetId, weight: Number(weight) },
      }),
    onSuccess: () => {
      setTerm("");
      return queryClient.invalidateQueries({ queryKey: keywordsKey });
    },
  });

  return (
    <section aria-labelledby="target-heading" className="flex flex-col gap-3">
      <h2 id="target-heading" className="text-lg font-semibold">
        案内先から確認・追加
      </h2>
      <div className="flex flex-wrap gap-2">
        <div className="flex flex-col gap-1 text-sm">
          <label htmlFor="target-type">種類</label>
          <select
            id="target-type"
            value={type}
            onChange={(e) => {
              setType(e.target.value as KeywordTargetType);
              setTargetId("");
            }}
            className="rounded-md border border-border bg-background px-2 py-2"
          >
            {Object.entries(TARGET_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
        <div className="flex min-w-0 flex-1 flex-col gap-1 text-sm">
          <label htmlFor="target-id">案内先</label>
          <select
            id="target-id"
            value={targetId}
            onChange={(e) => setTargetId(e.target.value)}
            disabled={!targets.data}
            className="rounded-md border border-border bg-background px-2 py-2"
          >
            <option value="">選んでください</option>
            {targets.data?.map((t) => (
              <option key={t.id} value={t.id}>
                {t.label}（{t.code}）
              </option>
            ))}
          </select>
        </div>
      </div>
      {targets.error && <p role="alert">{errorMessage(targets.error)}</p>}

      {targetId && (
        <div className="flex flex-col gap-3 text-sm">
          {keywords.data && keywords.data.length > 0 ? (
            <KeywordTable keywords={keywords.data} />
          ) : keywords.data ? (
            <p>この案内先にはまだ言葉が登録されていません。</p>
          ) : null}
          <form
            className="flex flex-wrap items-end gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              add.mutate();
            }}
          >
            <label className="flex min-w-0 flex-1 flex-col gap-1">
              追加する言葉
              <input
                value={term}
                onChange={(e) => setTerm(e.target.value)}
                maxLength={100}
                placeholder="例: 入籍"
                className="rounded-md border border-border bg-background px-3 py-2"
              />
            </label>
            <label className="flex w-24 flex-col gap-1">
              重み
              <input
                type="number"
                min={0.1}
                max={5}
                step={0.1}
                value={weight}
                onChange={(e) => setWeight(e.target.value)}
                className="rounded-md border border-border bg-background px-2 py-2"
              />
            </label>
            <button
              type="submit"
              disabled={!term.trim() || add.isPending}
              className="rounded-md bg-foreground px-4 py-2 font-medium text-background disabled:opacity-60"
            >
              追加
            </button>
          </form>
          <p className="text-xs text-muted-foreground">
            重みは 0.1〜5.0（既定 1.0）。大きいほど、この言葉が含まれたときに案内先が上位になります。
          </p>
          {add.error && <p role="alert">{errorMessage(add.error)}</p>}
        </div>
      )}
    </section>
  );
}

function KeywordTable({ keywords, showTarget = false }: { keywords: KeywordResponse[]; showTarget?: boolean }) {
  const queryClient = useQueryClient();
  const remove = useMutation({
    mutationFn: (id: string) => apiFetch<void>(`${BASE}/${encodeURIComponent(id)}`, { method: "DELETE" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keywordsKey }),
  });

  return (
    <>
      <table className="w-full border-collapse text-left">
        <thead>
          <tr className="border-b border-border">
            <th className="py-1 pr-3">言葉</th>
            {showTarget && <th className="py-1 pr-3">案内先</th>}
            <th className="py-1 pr-3">重み</th>
            <th className="py-1">
              <span className="sr-only">操作</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {keywords.map((k) => (
            <tr key={k.id} className="border-b border-border">
              <td className="py-1 pr-3">{k.term}</td>
              {showTarget && (
                <td className="py-1 pr-3">
                  <span className="mr-1 rounded bg-foreground/10 px-1.5 py-0.5 text-xs">
                    {TARGET_TYPE_LABELS[k.targetType]}
                  </span>
                  {/* 閲覧権限のない案内先は名前を出さない */}
                  {k.targetLabel ?? "（閲覧権限のない案内先）"}
                </td>
              )}
              <td className="py-1 pr-3">{k.weight.toFixed(1)}</td>
              <td className="py-1 text-right">
                <button
                  type="button"
                  disabled={remove.isPending}
                  onClick={() => {
                    if (window.confirm(`「${k.term}」を辞書から削除しますか？`)) remove.mutate(k.id);
                  }}
                  className="rounded-md border border-border px-2 py-0.5"
                >
                  削除
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {remove.error && <p role="alert">{errorMessage(remove.error)}</p>}
    </>
  );
}
