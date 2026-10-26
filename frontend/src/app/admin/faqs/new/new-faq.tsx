"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { ApiError, apiFetch } from "@/lib/api/http";
import type { AdminFaq } from "@/lib/api/types";
import { fetchMe, meQueryKey } from "@/lib/auth/api";
import { adminFaqsKey } from "../faq-list";
import { EMPTY_FAQ, FaqForm, type FaqFormValues, toContentRequest } from "../faq-form";

/**
 * FAQ を下書きとして作成する。所管部署はログイン中の担当者の部署になる。
 * 検索の分析（何も見つからなかった質問）から ?question= 付きで開かれたときは、その質問を入れた状態にする
 */
export function NewFaq() {
  const router = useRouter();
  const initialQuestion = useSearchParams().get("question")?.trim() ?? "";
  const queryClient = useQueryClient();
  const { data: me } = useQuery({ queryKey: meQueryKey, queryFn: fetchMe });
  const [code, setCode] = useState("FAQ-");
  const [values, setValues] = useState<FaqFormValues>({ ...EMPTY_FAQ, question: initialQuestion });

  const create = useMutation({
    mutationFn: () =>
      apiFetch<AdminFaq>("/api/v1/admin/faqs", {
        method: "POST",
        json: { code: code.trim(), ownerDepartmentCode: me?.department?.code, content: toContentRequest(values) },
      }),
    onSuccess: async (faq) => {
      await queryClient.invalidateQueries({ queryKey: adminFaqsKey });
      router.push(`/admin/faqs/${faq.id}`);
    },
  });

  return (
    <form
      className="flex flex-col gap-4"
      onSubmit={(e) => {
        e.preventDefault();
        create.mutate();
      }}
    >
      <h1 className="text-2xl font-bold">新しい FAQ</h1>
      <p className="text-sm text-muted-foreground">
        下書きとして保存します。所管部署: {me?.department?.name ?? "（所属なし）"}。公開は保存したあとに行います。
      </p>
      <div className="flex flex-col gap-1 text-sm">
        <label htmlFor="faq-code" className="font-medium">
          コード（英大文字・数字・ハイフン）
        </label>
        <input
          id="faq-code"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          maxLength={32}
          className="w-48 rounded-md border border-border bg-background px-3 py-2 font-mono"
        />
      </div>
      <FaqForm values={values} onChange={setValues} />
      {create.error && (
        <p role="alert" className="text-sm text-red-600 dark:text-red-400">
          {create.error instanceof ApiError ? create.error.detail : "保存できませんでした。"}
        </p>
      )}
      <button
        type="submit"
        disabled={create.isPending || !me?.department}
        className="self-start rounded-md bg-foreground px-4 py-2 font-medium text-background disabled:opacity-60"
      >
        下書きとして保存
      </button>
    </form>
  );
}
