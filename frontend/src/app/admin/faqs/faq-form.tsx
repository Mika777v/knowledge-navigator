"use client";

import { useState } from "react";
import { Markdown } from "@/components/markdown";
import type { AdminFaq, FaqContentRequest, Visibility } from "@/lib/api/types";
import { VISIBILITY_LABELS } from "@/lib/content-labels";
import { useContentOptions } from "../content-options";

/** 関連先の1行。制度か規程（条番号を入れるとその条）のどちらか */
export type LinkRow = { kind: "PROGRAM" | "DOCUMENT"; code: string; articleNo: string };

export type FaqFormValues = {
  question: string;
  answerMarkdown: string;
  categorySlug: string;
  contactCode: string;
  visibility: Visibility;
  links: LinkRow[];
};

export const EMPTY_FAQ: FaqFormValues = {
  question: "",
  answerMarkdown: "",
  categorySlug: "",
  contactCode: "",
  visibility: "INTERNAL",
  links: [],
};

/** API の FAQ をフォームの値にする（関連先はコードと条番号で持つ） */
export function toFormValues(faq: AdminFaq): FaqFormValues {
  return {
    question: faq.question,
    answerMarkdown: faq.answerMarkdown,
    categorySlug: faq.category?.slug ?? "",
    contactCode: faq.contactCode ?? "",
    visibility: faq.visibility,
    links: faq.links
      .filter((l) => l.programCode || l.documentCode)
      .map((l) =>
        l.programCode
          ? { kind: "PROGRAM", code: l.programCode, articleNo: "" }
          : { kind: "DOCUMENT", code: l.documentCode, articleNo: l.articleNo ?? "" },
      ),
  };
}

/** 関連先の送り方。制度ならコードだけ、規程ならコードと条番号（任意） */
type LinkPayload = Partial<NonNullable<FaqContentRequest["links"]>[number]>;

/** FAQ の更新内容（PUT の本文・作成時の content） */
export type FaqContentPayload = Omit<FaqContentRequest, "links"> & { links: LinkPayload[] };

/** フォームの値を API に送る形にする（空の項目は送らない） */
export function toContentRequest(values: FaqFormValues): FaqContentPayload {
  return {
    question: values.question.trim(),
    answerMarkdown: values.answerMarkdown,
    categorySlug: values.categorySlug,
    contactCode: values.contactCode || undefined,
    visibility: values.visibility,
    links: values.links
      .filter((l) => l.code)
      .map(
        (l): LinkPayload =>
          l.kind === "PROGRAM"
            ? { programCode: l.code }
            : { documentCode: l.code, articleNo: l.articleNo.trim() || undefined },
      ),
  };
}

const inputClass = "rounded-md border border-border bg-background px-3 py-2";

/**
 * FAQ の入力欄（新規作成と編集で共通）。回答は Markdown で、入力しながら表示を確認できる。
 * 選べる制度・規程は、この人が閲覧できる公開中のものだけ。
 */
export function FaqForm({
  values,
  onChange,
}: {
  values: FaqFormValues;
  onChange: (values: FaqFormValues) => void;
}) {
  const options = useContentOptions();
  const [preview, setPreview] = useState(false);
  const set = <K extends keyof FaqFormValues>(key: K, value: FaqFormValues[K]) => onChange({ ...values, [key]: value });
  const setLink = (index: number, link: Partial<LinkRow>) =>
    set(
      "links",
      values.links.map((l, i) => (i === index ? { ...l, ...link } : l)),
    );

  return (
    <div className="flex flex-col gap-4 text-sm">
      <div className="flex flex-col gap-1">
        <label htmlFor="faq-question" className="font-medium">
          質問
        </label>
        <input
          id="faq-question"
          value={values.question}
          onChange={(e) => set("question", e.target.value)}
          className={inputClass}
        />
      </div>

      <div className="flex flex-col gap-1">
        <div className="flex items-center justify-between">
          <label htmlFor="faq-answer" className="font-medium">
            回答（Markdown）
          </label>
          <button type="button" onClick={() => setPreview(!preview)} className="underline">
            {preview ? "編集に戻る" : "表示を確認"}
          </button>
        </div>
        {preview ? (
          <div className="rounded-md border border-border p-3">
            <Markdown text={values.answerMarkdown} />
          </div>
        ) : (
          <textarea
            id="faq-answer"
            value={values.answerMarkdown}
            onChange={(e) => set("answerMarkdown", e.target.value)}
            rows={8}
            className={`${inputClass} font-mono`}
          />
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <div className="flex flex-col gap-1">
          <label htmlFor="faq-category" className="font-medium">
            カテゴリ
          </label>
          <select
            id="faq-category"
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
          <label htmlFor="faq-contact" className="font-medium">
            問い合わせ先
          </label>
          <select
            id="faq-contact"
            value={values.contactCode}
            onChange={(e) => set("contactCode", e.target.value)}
            className={inputClass}
          >
            <option value="">なし（関連する制度の窓口を案内）</option>
            {options.contacts?.map((d) => (
              <optgroup key={d.code} label={d.name}>
                {d.contacts.map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.name}
                  </option>
                ))}
              </optgroup>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="faq-visibility" className="font-medium">
            公開範囲
          </label>
          <select
            id="faq-visibility"
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
        <legend className="px-1 font-medium">関連する制度・規程</legend>
        {values.links.length === 0 && <p className="text-muted-foreground">まだありません。</p>}
        {values.links.map((link, i) => (
          <div key={i} className="flex flex-wrap items-end gap-2">
            <div className="flex flex-col gap-1">
              <select
                aria-label={`関連先 ${i + 1} の種類`}
                value={link.kind}
                onChange={(e) => setLink(i, { kind: e.target.value as LinkRow["kind"], code: "", articleNo: "" })}
                className={inputClass}
              >
                <option value="PROGRAM">制度</option>
                <option value="DOCUMENT">規程</option>
              </select>
            </div>
            <div className="flex min-w-0 flex-1 flex-col gap-1">
              <select
                aria-label={`関連先 ${i + 1}`}
                value={link.code}
                onChange={(e) => setLink(i, { code: e.target.value })}
                className={inputClass}
              >
                <option value="">選んでください</option>
                {(link.kind === "PROGRAM" ? options.programs : options.documents)?.map((o) => (
                  <option key={o.code} value={o.code}>
                    {"name" in o ? o.name : o.title}（{o.code}）
                  </option>
                ))}
              </select>
            </div>
            {link.kind === "DOCUMENT" && (
              <div className="flex w-28 flex-col gap-1">
                <input
                  aria-label={`関連先 ${i + 1} の条`}
                  value={link.articleNo}
                  onChange={(e) => setLink(i, { articleNo: e.target.value })}
                  placeholder="第5条（任意）"
                  className={inputClass}
                />
              </div>
            )}
            <button
              type="button"
              onClick={() =>
                set(
                  "links",
                  values.links.filter((_, j) => j !== i),
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
          onClick={() => set("links", [...values.links, { kind: "PROGRAM", code: "", articleNo: "" }])}
          className="self-start underline"
        >
          関連先を追加
        </button>
      </fieldset>
    </div>
  );
}
