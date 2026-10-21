"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/http";
import type { CategoryNode, Visibility } from "@/lib/api/types";
import { VISIBILITY_LABELS } from "@/lib/content-labels";
import { DOCUMENT_TYPE_LABELS, type DocumentFormValues, type DocumentType } from "./document-api";

const inputClass = "rounded-md border border-border bg-background px-3 py-2";

/** 文書の情報の入力欄（新規作成と編集で共通）。本文は版ごとに入力する */
export function DocumentForm({
  values,
  onChange,
}: {
  values: DocumentFormValues;
  onChange: (values: DocumentFormValues) => void;
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
    </div>
  );
}
