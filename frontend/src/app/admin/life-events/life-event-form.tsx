"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/http";
import type { ProgramSummary } from "@/lib/api/types";
import { type LifeEventFormValues, RELATION_LABELS, type Relation } from "./life-event-api";

const inputClass = "rounded-md border border-border bg-background px-3 py-2";

/**
 * きっかけの入力欄（新規作成と編集で共通）。紐づける制度は、並べた順に社員の画面に表示される。
 * 選べる制度は、操作する人が閲覧できる公開中のもの。
 */
export function LifeEventForm({
  values,
  onChange,
}: {
  values: LifeEventFormValues;
  onChange: (values: LifeEventFormValues) => void;
}) {
  const { data: programs } = useQuery({
    queryKey: ["programs"],
    queryFn: () => apiFetch<ProgramSummary[]>("/api/v1/programs"),
  });
  const set = <K extends keyof LifeEventFormValues>(key: K, value: LifeEventFormValues[K]) =>
    onChange({ ...values, [key]: value });
  const rows = values.programs;
  const setRows = (next: LifeEventFormValues["programs"]) => set("programs", next);
  const move = (i: number, delta: number) => {
    const j = i + delta;
    if (j < 0 || j >= rows.length) return;
    const next = [...rows];
    [next[i], next[j]] = [next[j]!, next[i]!];
    setRows(next);
  };

  return (
    <div className="flex flex-col gap-4 text-sm">
      <div className="grid gap-4 sm:grid-cols-[1fr_10rem_8rem]">
        <div className="flex flex-col gap-1">
          <label htmlFor="life-event-name" className="font-medium">
            名前
          </label>
          <input
            id="life-event-name"
            value={values.name}
            onChange={(e) => set("name", e.target.value)}
            maxLength={50}
            className={inputClass}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="life-event-icon" className="font-medium">
            アイコン名（任意）
          </label>
          <input
            id="life-event-icon"
            value={values.icon}
            onChange={(e) => set("icon", e.target.value)}
            maxLength={20}
            className={inputClass}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="life-event-sort" className="font-medium">
            表示順
          </label>
          <input
            id="life-event-sort"
            type="number"
            min={0}
            max={1000}
            value={values.sortOrder}
            onChange={(e) => set("sortOrder", e.target.value)}
            className={inputClass}
          />
        </div>
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor="life-event-description" className="font-medium">
          説明（任意）
        </label>
        <textarea
          id="life-event-description"
          value={values.description}
          onChange={(e) => set("description", e.target.value)}
          maxLength={500}
          rows={2}
          className={inputClass}
        />
      </div>

      <fieldset className="flex flex-col gap-2 rounded-md border border-border p-3">
        <legend className="px-1 font-medium">紐づける制度（並べた順に表示）</legend>
        {rows.length === 0 && (
          <p className="text-muted-foreground">「まず確認する制度」を1つ以上選んでください。</p>
        )}
        {rows.map((row, i) => (
          <div key={i} className="flex flex-wrap items-end gap-2">
            <div className="flex min-w-0 flex-1 flex-col gap-1">
              {/* 行ごとの選択欄は見出しを出さず、読み上げ用の名前だけを付ける */}
              <select
                aria-label={`制度 ${i + 1}`}
                value={row.programCode}
                onChange={(e) => setRows(rows.map((r, j) => (j === i ? { ...r, programCode: e.target.value } : r)))}
                className={inputClass}
              >
                <option value="">選んでください</option>
                {/* 公開中でない制度が紐づいていても、選択を失わないよう残す */}
                {row.programCode && !programs?.some((p) => p.code === row.programCode) && (
                  <option value={row.programCode}>{row.programCode}</option>
                )}
                {programs?.map((p) => (
                  <option key={p.code} value={p.code}>
                    {p.name}（{p.code}）
                  </option>
                ))}
              </select>
            </div>
            <div className="flex w-48 flex-col gap-1">
              <select
                aria-label={`制度 ${i + 1} の種類`}
                value={row.relation}
                onChange={(e) =>
                  setRows(rows.map((r, j) => (j === i ? { ...r, relation: e.target.value as Relation } : r)))
                }
                className={inputClass}
              >
                {Object.entries(RELATION_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
            <button
              type="button"
              aria-label={`制度 ${i + 1} を上へ`}
              disabled={i === 0}
              onClick={() => move(i, -1)}
              className="rounded-md border border-border px-2 py-2 disabled:opacity-40"
            >
              ↑
            </button>
            <button
              type="button"
              aria-label={`制度 ${i + 1} を下へ`}
              disabled={i === rows.length - 1}
              onClick={() => move(i, 1)}
              className="rounded-md border border-border px-2 py-2 disabled:opacity-40"
            >
              ↓
            </button>
            <button
              type="button"
              onClick={() => setRows(rows.filter((_, j) => j !== i))}
              className="rounded-md border border-border px-2 py-2"
            >
              外す
            </button>
          </div>
        ))}
        <button
          type="button"
          onClick={() => setRows([...rows, { programCode: "", relation: rows.length === 0 ? "PRIMARY" : "RELATED" }])}
          className="self-start underline"
        >
          制度を追加
        </button>
      </fieldset>
      <p className="text-xs text-muted-foreground">
        検索で使う言い方（例: 「入籍」→ 結婚）は、キーワード辞書できっかけに追加してください。
      </p>
    </div>
  );
}
