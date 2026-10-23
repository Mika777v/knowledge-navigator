"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/http";
import { useState } from "react";
import type { CategoryNode, TagCount, Visibility } from "@/lib/api/types";
import { VISIBILITY_LABELS } from "@/lib/content-labels";
import { DOCUMENT_TYPE_LABELS, type DocumentFormValues, type DocumentType } from "./document-api";

const inputClass = "rounded-md border border-border bg-background px-3 py-2";

/** 文書の情報の入力欄（新規作成と編集で共通）。本文は版ごとに入力する */
export function DocumentForm({
  values,
  onChange,
  withTags = false,
}: {
  values: DocumentFormValues;
  onChange: (values: DocumentFormValues) => void;
  /** タグの入力欄を出す（タグは作成後に付ける） */
  withTags?: boolean;
}) {
  const { data: categories } = useQuery({
    queryKey: ["categories"],
    queryFn: () => apiFetch<CategoryNode[]>("/api/v1/categories"),
  });
  const set = <K extends keyof DocumentFormValues>(key: K, value: DocumentFormValues[K]) =>
    onChange({ ...values, [key]: value });

  return (
    <div className="flex flex-col gap-4 text-sm">
      <div className="flex flex-col gap-1">
        <label htmlFor="document-title" className="font-medium">
          文書名
        </label>
        <input
          id="document-title"
          value={values.title}
          onChange={(e) => set("title", e.target.value)}
          maxLength={200}
          className={inputClass}
        />
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor="document-summary" className="font-medium">
          概要（任意）
        </label>
        <textarea
          id="document-summary"
          value={values.summary}
          onChange={(e) => set("summary", e.target.value)}
          rows={2}
          className={inputClass}
        />
      </div>
      <div className="grid gap-4 sm:grid-cols-4">
        <div className="flex flex-col gap-1">
          <label htmlFor="document-type" className="font-medium">
            種類
          </label>
          <select
            id="document-type"
            value={values.documentType}
            onChange={(e) => set("documentType", e.target.value as DocumentType)}
            className={inputClass}
          >
            {Object.entries(DOCUMENT_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="document-category" className="font-medium">
            カテゴリ
          </label>
          <select
            id="document-category"
            value={values.categorySlug}
            onChange={(e) => set("categorySlug", e.target.value)}
            className={inputClass}
          >
            <option value="">選んでください</option>
            {categories?.map((parent) => (
              <optgroup key={parent.slug} label={parent.name}>
                <option value={parent.slug}>{parent.name}</option>
                {parent.children.map((c) => (
                  <option key={c.slug} value={c.slug}>
                    {parent.name} / {c.name}
                  </option>
                ))}
              </optgroup>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="document-visibility" className="font-medium">
            公開範囲
          </label>
          <select
            id="document-visibility"
            value={values.visibility}
            onChange={(e) => set("visibility", e.target.value as Visibility)}
            className={inputClass}
          >
            {Object.entries(VISIBILITY_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="document-review" className="font-medium">
            見直し期限（任意）
          </label>
          <input
            id="document-review"
            type="date"
            value={values.reviewDueOn}
            onChange={(e) => set("reviewDueOn", e.target.value)}
            className={inputClass}
          />
        </div>
      </div>
      {withTags && <TagPicker tags={values.tags} onChange={(tags) => set("tags", tags)} />}
    </div>
  );
}

const TAG_TYPE_LABELS: Record<string, string> = { TARGET: "対象者", TOPIC: "話題", FREE: "その他" };

/** 文書のタグ。使われているタグから選ぶか、新しい名前を入れて追加する */
function TagPicker({ tags, onChange }: { tags: string[]; onChange: (tags: string[]) => void }) {
  const { data: known } = useQuery({
    queryKey: ["tags"],
    queryFn: () => apiFetch<TagCount[]>("/api/v1/tags"),
  });
  const [draft, setDraft] = useState("");
  const toggle = (name: string) => onChange(tags.includes(name) ? tags.filter((t) => t !== name) : [...tags, name]);
  const add = () => {
    const name = draft.trim();
    if (name && !tags.includes(name)) onChange([...tags, name]);
    setDraft("");
  };
  const groups = Object.entries(TAG_TYPE_LABELS)
    .map(([type, label]) => ({ label, names: (known ?? []).filter((t) => t.type === type).map((t) => t.name) }))
    .filter((g) => g.names.length > 0);
  const others = tags.filter((t) => !known?.some((k) => k.name === t));

  return (
    <fieldset className="flex flex-col gap-2 rounded-md border border-border p-3">
      <legend className="px-1 font-medium">タグ（10個まで）</legend>
      <p className="text-xs text-muted-foreground">社員が規程の一覧で絞り込むときに使います。</p>
      {[...groups, ...(others.length ? [{ label: "新しいタグ", names: others }] : [])].map((g) => (
        <div key={g.label} className="flex flex-wrap items-center gap-2">
          <span className="w-16 text-xs text-muted-foreground">{g.label}</span>
          {g.names.map((name) => (
            <button
              key={name}
              type="button"
              aria-pressed={tags.includes(name)}
              onClick={() => toggle(name)}
              className="rounded-full border border-border px-3 py-0.5 aria-pressed:bg-foreground aria-pressed:text-background"
            >
              {name}
            </button>
          ))}
        </div>
      ))}
      <div className="flex gap-2">
        <label htmlFor="document-new-tag" className="sr-only">
          新しいタグ
        </label>
        <input
          id="document-new-tag"
          value={draft}
          maxLength={50}
          placeholder="新しいタグの名前"
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              add();
            }
          }}
          className={`${inputClass} flex-1`}
        />
        <button type="button" onClick={add} disabled={!draft.trim()} className="rounded-md border border-border px-3">
          タグを追加
        </button>
      </div>
    </fieldset>
  );
}
