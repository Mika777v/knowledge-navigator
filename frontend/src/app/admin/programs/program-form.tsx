"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/http";
import type { DocumentDetail, Visibility } from "@/lib/api/types";
import { VISIBILITY_LABELS } from "@/lib/content-labels";
import { useContentOptions } from "../content-options";
import { CONTACT_ROLE_LABELS, type ContactRole, type ProgramFormValues } from "./program-api";

const inputClass = "rounded-md border border-border bg-background px-3 py-2";

/** 規程の公開版の条（根拠に選ぶ条番号の候補） */
function ArticleSelect({
  documentId,
  value,
  onChange,
  label,
}: {
  documentId: string | undefined;
  value: string;
  onChange: (articleNo: string) => void;
  label: string;
}) {
  const { data } = useQuery({
    queryKey: ["document", documentId, null],
    queryFn: () => apiFetch<DocumentDetail>(`/api/v1/documents/${encodeURIComponent(documentId ?? "")}`),
    enabled: !!documentId,
  });
  const articles = (data?.sections ?? []).filter((s) => s.articleNo);
  return (
    <div className="flex w-56 flex-col gap-1">
      <select
        aria-label={label}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={!documentId}
        className={inputClass}
      >
        <option value="">文書全体</option>
        {/* 保存済みの条が公開版にない場合も、選択を失わないよう残す */}
        {value && !articles.some((s) => s.articleNo === value) && <option value={value}>{value}</option>}
        {articles.map((s) => (
          <option key={s.id} value={s.articleNo ?? ""}>
            {s.articleNo}
            {s.heading ? `（${s.heading}）` : ""}
          </option>
        ))}
      </select>
    </div>
  );
}

/**
 * 制度の入力欄（新規作成と編集で共通）。説明・根拠の条・窓口を入力する。
 * 根拠に選べる規程は、操作する人が閲覧できる公開中のものだけ。
 */
export function ProgramForm({
  values,
  onChange,
}: {
  values: ProgramFormValues;
  onChange: (values: ProgramFormValues) => void;
}) {
  const options = useContentOptions();
  const set = <K extends keyof ProgramFormValues>(key: K, value: ProgramFormValues[K]) =>
    onChange({ ...values, [key]: value });
  const text = (key: keyof ProgramFormValues, label: string, multiline = false, hint?: string) => (
    <div className="flex flex-col gap-1">
      <label htmlFor={`program-${key}`} className="font-medium">
        {label}
      </label>
      {multiline ? (
        <textarea
          id={`program-${key}`}
          value={values[key] as string}
          onChange={(e) => set(key, e.target.value as never)}
          rows={3}
          className={inputClass}
        />
      ) : (
        <input
          id={`program-${key}`}
          value={values[key] as string}
          onChange={(e) => set(key, e.target.value as never)}
          className={inputClass}
        />
      )}
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  );

  return (
    <div className="flex flex-col gap-4 text-sm">
      <div className="grid gap-4 sm:grid-cols-2">
        {text("name", "制度名")}
        {text("nameKana", "読みがな（任意）")}
      </div>
      {text("summary", "概要", true, "検索結果に出る説明です。1〜2文で、何がもらえる・できる制度かを書きます。")}
      <div className="grid gap-4 sm:grid-cols-2">
        {text("eligibility", "対象者", true)}
        {text("benefit", "内容（金額・日数など）", true)}
        {text("howToApply", "申請方法", true)}
        {text("requiredDocuments", "必要書類", true)}
      </div>
      {text("deadlineNote", "期限")}

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <label htmlFor="program-category" className="font-medium">
            カテゴリ
          </label>
          <select
            id="program-category"
            value={values.categorySlug}
            onChange={(e) => set("categorySlug", e.target.value)}
            className={inputClass}
          >
            <option value="">選んでください</option>
            {options.categories?.map((parent) => (
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
          <label htmlFor="program-visibility" className="font-medium">
            公開範囲
          </label>
          <select
            id="program-visibility"
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
      </div>

      <fieldset className="flex flex-col gap-2 rounded-md border border-border p-3">
        <legend className="px-1 font-medium">根拠となる規程の条</legend>
        {values.basis.length === 0 && <p className="text-muted-foreground">公開するには1件以上必要です。</p>}
        {values.basis.map((b, i) => {
          const doc = options.documents?.find((d) => d.code === b.documentCode);
          return (
            <div key={i} className="flex flex-wrap items-end gap-2">
              <div className="flex min-w-0 flex-1 flex-col gap-1">
                <select
                  aria-label={`根拠 ${i + 1} の規程`}
                  value={b.documentCode}
                  onChange={(e) =>
                    set(
                      "basis",
                      values.basis.map((x, j) => (j === i ? { documentCode: e.target.value, articleNo: "" } : x)),
                    )
                  }
                  className={inputClass}
                >
                  <option value="">選んでください</option>
                  {b.documentCode && !doc && <option value={b.documentCode}>{b.documentCode}</option>}
                  {options.documents?.map((d) => (
                    <option key={d.code} value={d.code}>
                      {d.title}（{d.code}）
                    </option>
                  ))}
                </select>
              </div>
              <ArticleSelect
                documentId={doc?.id}
                value={b.articleNo}
                label={`根拠 ${i + 1} の条`}
                onChange={(articleNo) =>
                  set(
                    "basis",
                    values.basis.map((x, j) => (j === i ? { ...x, articleNo } : x)),
                  )
                }
              />
              <button
                type="button"
                onClick={() =>
                  set(
                    "basis",
                    values.basis.filter((_, j) => j !== i),
                  )
                }
                className="rounded-md border border-border px-2 py-2"
              >
                外す
              </button>
            </div>
          );
        })}
        <button
          type="button"
          onClick={() => set("basis", [...values.basis, { documentCode: "", articleNo: "" }])}
          className="self-start underline"
        >
          根拠を追加
        </button>
      </fieldset>

      <fieldset className="flex flex-col gap-2 rounded-md border border-border p-3">
        <legend className="px-1 font-medium">窓口</legend>
        {values.contacts.length === 0 && <p className="text-muted-foreground">公開するには1件以上必要です。</p>}
        {values.contacts.map((c, i) => (
          <div key={i} className="flex flex-wrap items-end gap-2">
            <div className="flex min-w-0 flex-1 flex-col gap-1">
              <select
                aria-label={`窓口 ${i + 1}`}
                value={c.contactCode}
                onChange={(e) =>
                  set(
                    "contacts",
                    values.contacts.map((x, j) => (j === i ? { ...x, contactCode: e.target.value } : x)),
                  )
                }
                className={inputClass}
              >
                <option value="">選んでください</option>
                {options.contacts?.map((d) => (
                  <optgroup key={d.code} label={d.name}>
                    {d.contacts.map((ct) => (
                      <option key={ct.code} value={ct.code}>
                        {ct.name}
                      </option>
                    ))}
                  </optgroup>
                ))}
              </select>
            </div>
            <div className="flex w-36 flex-col gap-1">
              <select
                aria-label={`窓口 ${i + 1} の役割`}
                value={c.role}
                onChange={(e) =>
                  set(
                    "contacts",
                    values.contacts.map((x, j) => (j === i ? { ...x, role: e.target.value as ContactRole } : x)),
                  )
                }
                className={inputClass}
              >
                {Object.entries(CONTACT_ROLE_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
            <button
              type="button"
              onClick={() =>
                set(
                  "contacts",
                  values.contacts.filter((_, j) => j !== i),
                )
              }
              className="rounded-md border border-border px-2 py-2"
            >
              外す
            </button>
          </div>
        ))}
        <button
          type="button"
          onClick={() => set("contacts", [...values.contacts, { contactCode: "", role: "INQUIRY" }])}
          className="self-start underline"
        >
          窓口を追加
        </button>
      </fieldset>
    </div>
  );
}
